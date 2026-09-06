import com.jamesward.zio_evals.*
import com.jamesward.zio_evals.cli.{ClaudeCliAgentLoop, KiroCliAgentLoop}
import zio.*

object Main extends ZIOAppDefault:

  private val backendName = sys.props.getOrElse("eval.backend", "kiro").toLowerCase
  private val samples     = sys.props.get("eval.samples").flatMap(_.toIntOption).filter(_ > 0).getOrElse(1)

  private val modelId = sys.props.get("eval.model").filter(_.nonEmpty).getOrElse {
    if backendName == "claude" then "claude-sonnet-4-6" else ""
  }

  private val judgeModelId = sys.props.get("eval.judgeModel").filter(_.nonEmpty).getOrElse(modelId)

  private val zenOfJames = AgentSkill(
    "zen-of-james",
    SkillSource.Classpath("META-INF/skills/jamesward/skills/zen-of-james/SKILL.md"),
  )

  private val spec = EvalSpec(
    task =
      """Write a single Scala 3 source file using ZIO 2 that implements an order-intake component.
        |
        |Inputs arrive as untrusted strings and include a customer ID, item IDs, order status, and fulfillment information. An order must contain at least one item. Fulfillment is either delivery to an address or pickup from a store, but never both. The component imports orders from a file, persists accepted orders, and writes audit events.
        |
        |Include the public API, domain model, parsing, implementation, and representative ZIO Test tests. Include separate tests that directly exercise pure domain/parsing functions and tests that exercise effectful OrderIntake service behavior using fake repository, audit, and file implementations. Use assertTrue for assertions. Return only Scala code. You may use the Scala standard library, ZIO 2, and zio-test.""".stripMargin,
    criteria =
      """Grade the generated Scala code strictly against the zen-of-james coding principles below. PASS only when requirements 1-7 are all satisfied and at least seven of the eight total requirements are satisfied. Do not award credit for prose that merely claims a property; it must be represented in the code.
        |
        |1. Illegal states are unrepresentable: fulfillment is a closed ADT with delivery and pickup cases rather than independent Options; order items use a constructor-enforced non-empty type rather than List plus runtime require.
        |2. Domain-oriented types: primitive IDs and addresses are declared as Scala opaque types with boundary parsers. Case classes, value classes, and aliases around those primitives do not satisfy this requirement; an existing domain-specific standard type such as java.nio.file.Path is acceptable.
        |3. Parse, do not validate: untrusted strings are parsed once at the boundary into domain values and failures are typed rather than thrown or represented only by arbitrary strings.
        |4. Closed branching and immutability: statuses and fulfillment are enums/sealed ADTs, production decisions over domain state are exhaustive, and there is no var, mutable collection, or hidden mutable service state. Catch-all branches used only as negative test-assertion fallbacks are acceptable.
        |5. Effects and testability: persistence, auditing, and file access are represented as ZIO effects behind injected domain interfaces.
        |6. Resource safety: file handles are guaranteed to close with bracketed handling such as Scope/acquireRelease/Using, or file access uses a managed convenience API such as Files.readAllLines that opens and closes internally; code must not leak a Source, reader, stream, or other closeable handle.
        |7. Tests use assertTrue and show the test onion with fast pure domain/parsing tests plus service integration tests using fake implementations.
        |8. Multiversal equality is enabled with domain-appropriate CanEqual instances/derivations; universal Any equality or omitting equality capabilities fails this requirement.
        |
        |FAIL when any requirement 1-7 is missing or fewer than seven total requirements are satisfied, even if the code would otherwise compile.""".stripMargin,
    checks = List(
      EvalCheck.AnswerMatches("(?s)\\bopaque\\s+type\\s+[A-Za-z][A-Za-z0-9]*(?:Id|Address)\\b"),
      EvalCheck.AnswerMatches("(?s)\\b(?:NonEmpty[A-Za-z0-9]*|OrderItems)\\b"),
      EvalCheck.AnswerMatches("(?s)\\b(?:enum|sealed\\s+(?:trait|abstract\\s+class))\\s+(?:Fulfillment|OrderStatus|Status)\\b"),
      EvalCheck.AnswerMatches("(?s)(?:ZIO\\s*\\.\\s*(?:acquireRelease(?:With)?|fromAutoCloseable|scoped)|Using\\s*\\.\\s*resource|Files\\s*\\.\\s*readAllLines)"),
      EvalCheck.AnswerMatches("(?s)\\bassertTrue\\s*\\("),
      EvalCheck.AnswerNotMatches("(?m)^\\s*(?:(?:private|protected)\\s+)?var\\s+"),
      EvalCheck.AnswerNotMatches("(?s)\\brequire\\s*\\([^)]*(?:nonEmpty|isEmpty)"),
    ),
  )

  private val arms = List(
    EvalArm.modelOnly("a", "A"),
    EvalArm.withSkills("b", "B", AgentSkills.explicit(zenOfJames)),
  )

  private val backendAndPreflight: (AgentLoop, Task[Unit]) =
    backendName match
      case "kiro" =>
        val backend = KiroCliAgentLoop(runTimeout = 5.minutes)
        (backend, KiroCliAgentLoop.validate)
      case "claude" =>
        val backend = ClaudeCliAgentLoop(
          maxBudgetUsd = sys.props.getOrElse("eval.maxBudgetUsd", "1.00"),
          runTimeout   = 5.minutes,
        )
        (backend, ClaudeCliAgentLoop.validate)
      case other =>
        throw IllegalArgumentException(s"unsupported eval.backend '$other'; expected kiro or claude")

  private val agentLoop = backendAndPreflight._1
  private val preflight = backendAndPreflight._2

  private def summary(result: ArmResult): String =
    f"${result.arm.label}: verdict=${result.verdict} passRate=${result.passRate}%.2f checks=${result.checksPassed} " +
      f"latencyMs=${result.metrics.latencyMs}%.0f rationale=${result.rationale}"

  def run =
    val judge = AgentLoopJudge(agentLoop, judgeModelId)
    for
      _       <- Console.printLine(s"Running zen-of-james eval: backend=$backendName model=${Option(modelId).filter(_.nonEmpty).getOrElse("default")} samples=$samples")
      _       <- preflight
      results <- EvalRunner.run(spec, arms, List(modelId), samples, agentLoop, judge)
      _       <- ZIO.foreachDiscard(results)(r => Console.printLine(summary(r)))
      baseline <- ZIO.fromOption(results.find(_.arm.name == "a")).orElseFail(RuntimeException("missing baseline result"))
      treatment <- ZIO.fromOption(results.find(_.arm.name == "b")).orElseFail(RuntimeException("missing treatment result"))
      baselineFailed = baseline.verdict == EvalVerdict.Fail
      treatmentPassed = treatment.verdict == EvalVerdict.Pass
      _ <- ZIO.fail(RuntimeException(
             s"expected baseline FAIL and zen-of-james PASS, got ${summary(baseline)}; ${summary(treatment)}"
           )).unless(baselineFailed && treatmentPassed)
      _ <- Console.printLine("Expected contrast confirmed: baseline FAIL, zen-of-james PASS")
    yield ()
