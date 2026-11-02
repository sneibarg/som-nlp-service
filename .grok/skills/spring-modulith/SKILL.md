---
name: spring-modulith
description: "Lay out Spring Boot Modulith modules, enforce allowed module dependencies, and check that module tests meet Modulith verification requirements. Recommends the Modulith features that fit the change, including application modules, named interfaces, application events, module documentation, and ApplicationModules.verify(). Use when structuring Java or Spring Boot code into modules, fixing boundary violations, writing Modulith tests, or when the user runs /spring-modulith."
---

# Spring Modulith

Apply this to the open project. Read the code and build file before choosing a structure. Preserve behavior while moving types.

## Workflow

1. Read the build file and the `@SpringBootApplication` class. Record the Spring Boot version, the Java version, and every `org.springframework.modulith` artifact already declared.
2. Treat the application class package as the application base package. List its direct subpackages and the types that sit in the base package itself. That list is the module model Modulith will see, including when Modulith is not on the classpath yet.
3. Choose features from the table below. Tell the user the module map, the dependencies you will allow, the features you will add, and the features you will leave out. Implement after that, unless the user asked only for a recommendation.
4. Lay the modules out.
5. Add the dependencies and the structural test. Run `ApplicationModules.verify()` and fix every violation.
6. Add or update module tests until the test checklist passes, then run those tests.

## Choose features

Pick a row only when its need is present. Leave the others out.

| Need | Use |
| --- | --- |
| Fail the build when modules depend on each other illegally | `ApplicationModules.verify()` |
| Let another module see a package other than the module base package | `@NamedInterface` on that package |
| Freeze which modules one module may touch | `@ApplicationModule(allowedDependencies = …)` |
| Let another module react without a bean dependency on the publisher | `ApplicationEventPublisher` in the publisher, and `org.springframework.modulith.events.ApplicationModuleListener` on the listener |
| Retry a listener that failed or never ran | The Event Publication Registry starter that matches the database already in the project |
| Send an event to a broker the application already uses | `@Externalized` and the matching `spring-modulith-events-kafka`, `spring-modulith-events-amqp`, or `spring-modulith-events-jms` artifact |
| Run startup code in module-dependency order | `ApplicationModuleInitializer` |
| Expose the module graph at runtime | `spring-modulith-starter-insight` |
| Produce C4 diagrams or an application-module canvas | `Documenter` |
| Run code because an hour, day, week, month, quarter, or year passed | Moments types such as `HourHasPassed` and `DayHasPassed` |
| Keep a legacy module's internal types visible until a later change | `@ApplicationModule(type = Type.OPEN)` on that module |
| Split one module into smaller modules | A nested package annotated with `@ApplicationModule` |
| Skip tests for modules a change did not affect | `spring-modulith-junit` |

`Type.OPEN` is a transition. Say that in the module's `package-info.java`. A nested module is visible to its parent and to sibling nested modules, and it can see its parent's internal types.

Use `org.springframework.modulith.events.ApplicationModuleListener`. The annotation in `org.springframework.modulith` is deprecated. The events annotation is `@Async`, `@Transactional(propagation = REQUIRES_NEW)`, and `@TransactionalEventListener`. Add `@EnableAsync` when the application does not already enable async execution.

## Dependencies

Use the Modulith generation compiled against this project's Spring Boot generation:

| Spring Boot | Modulith |
| --- | --- |
| 4.1 | 2.1 |
| 4.0 | 2.0 |
| 3.5 | 1.4 |
| 3.4 | 1.3 |
| 3.3 | 1.2 |
| 3.2 | 1.1 |

Import the newest stable patch of that generation. Confirm the patch in the [Spring Modulith compatibility appendix](https://docs.spring.io/spring-modulith/reference/appendix.html) or on Maven Central before writing the version. If the Boot generation is not in the table, use the generation that appendix lists as compiled against it. Do not use a milestone or snapshot unless the project already does. Do not move to a newer Modulith generation than the one compiled against this Boot generation.

Import `org.springframework.modulith:spring-modulith-bom` in `dependencyManagement` with `<type>pom</type>` and `<scope>import</scope>`. Leave versions off the individual artifacts. On Gradle, import the same BOM and put the artifacts in `implementation`, `testImplementation`, or `runtimeOnly`.

Add `spring-modulith-starter-test` with test scope whenever you add Modulith.

Add `spring-modulith-starter-core` for the module model, `@NamedInterface`, `@ApplicationModule`, `ApplicationModuleInitializer`, and Moments. A persistence event starter already brings in `starter-core` and `spring-modulith-events-api`, so use that starter instead of `starter-core` when the registry is selected:

| Database already used | Starter |
| --- | --- |
| JDBC or Spring Data JDBC | `spring-modulith-starter-jdbc` |
| JPA | `spring-modulith-starter-jpa` |
| MongoDB | `spring-modulith-starter-mongodb` |
| Neo4j | `spring-modulith-starter-neo4j` |

`@ApplicationModuleListener` needs `spring-modulith-events-api` when no persistence event starter is on the classpath. Do not add a second copy of an artifact a starter already includes. Add `spring-modulith-starter-insight` with runtime scope only for the insight row. Add broker artifacts only for the broker row.

## Lay out modules

Direct subpackages of the application base package are modules. Their subpackages belong to that module and are internal. Types in the application base package belong to no module and are visible to every module. Keep that package to the application class and types that are genuinely shared.

Name a module after a capability, as one direct subpackage. If the direct subpackages are technical layers such as `controller`, `service`, `repository`, `config`, or `dto`, those layers become modules and will depend on each other. Move each capability into its own direct subpackage and keep the layer packages inside it. If one direct subpackage only groups several capabilities, promote each capability to a direct subpackage of the application base package.

Public types that other modules may use stay in the module base package. Put everything else in a subpackage, conventionally `internal`. Put a published event type in the module base package, or in a package annotated with `@NamedInterface`, so other modules can name the event without seeing internals.

Add `package-info.java` when the module needs an allow-list, a display name, or `Type.OPEN`. Other modules do not need the annotation.

```java
@org.springframework.modulith.ApplicationModule(
  allowedDependencies = { "order", "order :: spi" }
)
package com.example.inventory;
```

`allowedDependencies` is an allow-list. `"order"` allows the module base package only. `"order :: spi"` allows the named interface and not the base package. `"order :: *"` allows every named interface of `order`. List both entries when the module needs both.

```java
@org.springframework.modulith.NamedInterface("spi")
package com.example.order.spi;
```

On Kotlin, put `@ApplicationModule` on a type in the module root that is also annotated with `@PackageInfo`.

Move types and fix references. Do not rewrite business logic as part of the move. If the application class cannot sit in the parent of the modules, ask before relocating it. Use `@Modulithic(additionalPackages = …)` only when the user wants detection to stay on the current application class.

## Enforce boundaries

Add this test next to the application class in the test tree, using the real application class:

```java
class ModularityTests {

  @Test
  void verifiesModuleStructure() {
    ApplicationModules.of(Application.class).verify();
  }
}
```

`org.springframework.modulith.core.ApplicationModules.verify()` checks three rules:

- Module dependencies form a directed acyclic graph.
- A module refers only to another module's base package and named interfaces. Dependencies on an open module's internal types are allowed.
- A module that declares `allowedDependencies` depends only on those targets. Code that belongs to no module stays reachable.

When jMolecules is already on the classpath, `verify()` also runs its domain-driven design rules. Fix those violations in the type or package. Do not add jMolecules for this, and do not remove it when the project already uses it.

Fix a violation in the structure. For a cycle, invert the edge with an event from the feature table, or extract a third module. For a reference to an internal type, move that type to the provider's API, expose a named interface, or stop using it. For an allow-list miss, remove the reference, or add the target when that edge is one you already told the user you would allow.

Do not make `verify()` pass by setting `verifyAutomatically = false`, excluding a project package from `ApplicationModules.of`, or marking a module `Type.OPEN`. Exclude a package only for generated or third-party types that are outside the modular model, and name those packages in the test.

Run the structural test with the project wrapper. On this repository that is `.\mvnw.cmd -Dtest=ModularityTests test`. Fix and run again until it passes.

## Tests

Meet this checklist before finishing:

- `ModularityTests` exists and `verify()` passes.
- Every module that contains a Spring bean has an `@ApplicationModuleTest` in that module's package or a subpackage. A module with no Spring beans is covered by `verify()` alone.
- A module test that publishes or consumes an event asserts that interaction with `Scenario` or `AssertablePublishedEvents`.
- `verifyAutomatically` stays at its default, `true`.
- New `Type.OPEN`, package exclusions, and allow-list entries are ones you already recommended, with the reason on the `package-info.java` or the structural test.

Place `@ApplicationModuleTest` (`org.springframework.modulith.test.ApplicationModuleTest`) in the module it bootstraps. Set `module` only when the test cannot live there. The default mode is `STANDALONE`: this module only. Use `DIRECT_DEPENDENCIES` or `ALL_DEPENDENCIES` when the assertion needs the real upstream module. Use `extraIncludes` for a module that is not a dependency but must be present. Replace a missing bean from another module with the mock-bean annotation this Boot generation uses, `@MockitoBean` on Boot 3.4 and later and `@MockBean` before that, instead of widening the slice.

```java
@ApplicationModuleTest
class OrderModuleTests {

  @Test
  void completesOrder(Scenario scenario) {
    scenario.stimulate(() -> orders.complete(orderId))
        .andWaitForEventOfType(OrderCompleted.class)
        .toArrive();
  }
}
```

`scenario.publish(event)` is the stimulus when the test starts from an event. `andWaitForStateChange` plus `andVerify` asserts a state change instead of an event. The stimulus runs in a new transaction, and its database writes are not rolled back. Clean them with `andCleanup`.

Match the assertion and visibility style of the existing tests. Leave a passing `@SpringBootTest` in place when it still covers a whole-application path. Use `@ApplicationModuleTest` for a new test of one module.

For canvases and diagrams, only when that row was selected:

```java
new Documenter(ApplicationModules.of(Application.class).verify())
    .writeDocumentation()
    .writeIndividualModulesAsPlantUml();
```

The output lands under the build directory. Do not commit it unless the user asks.
