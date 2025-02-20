
# Module templates_pebble

[Pebble] template engine adapter for Hexagon.

For usage instructions, refer to the [Templates Port documentation](/port_templates/).

[Pebble]: https://pebbletemplates.io

### Install the Dependency

=== "build.gradle"

    ```groovy
    repositories {
        mavenCentral()
    }

    implementation("com.hexagonkt:templates_pebble:$hexagonVersion")
    ```

=== "pom.xml"

    ```xml
    <dependency>
      <groupId>com.hexagonkt</groupId>
      <artifactId>templates_pebble</artifactId>
      <version>$hexagonVersion</version>
    </dependency>
    ```

# Package com.hexagonkt.templates.pebble

Classes that implement the Templates Port interface with the [Pebble] engine.
