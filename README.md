# executable-packer-maven-plugin

## Introduction

This maven plugin is designed to create executable JAR files for stand-alone applications containing all dependencies as JAR files as well (referred to as jar-in-jar-packaging).

## Quick start

Quickly want to create a packaged JAR file? No problem!

1. Add the following snippet to your `pom.xml`, in between `<build><plugins>   </plugins></build>`:

    ```
    <plugin>
    	<groupId>de.ntcomputer</groupId>
    	<artifactId>executable-packer-maven-plugin</artifactId>
    	<version>1.0.0</version>
    	<configuration>
    		<mainClass>com.example.MyMainClass</mainClass>
    	</configuration>
    	<executions>
    		<execution>
    			<goals>
    				<goal>pack-executable-jar</goal>
    			</goals>
    		</execution>
    	</executions>
    </plugin>
    ```

    Be sure to replace the `<mainClass>` value with your own main class which should be executed when running the JAR.

2. Run maven with goal `package`, e.g. `mvn package`
3. The created JAR file is located at `target/<YourProjectAndVersion>-pkg.jar` - you can simply run it by using `java -jar <YourProjectAndVersion>-pkg.jar`

## How it works

The plugin includes transitive compile-time and run-time project dependencies as JAR files inside the packaged JAR file (jar-in-jar-packaging).
Since the standard classloaders are not able to load classes from JAR files inside JAR files, the plugin also adds boilerplate code that provides a jar-in-jar-classloader and redirects the main class invocation to register this classloader on startup.

The steps that are taken in detail:

**At compile-time** (when you run `mvn package`):

1. Search for all compile-time and run-time JAR dependency artifacts
2. Configure JAR manifest: add application's main class, launcher's main class, dependency library path and list of dependency JARs to the manifest
3. Add all classes and resources to the created JAR file
4. Add all dependency JAR files (found in step 1) to the JAR file
5. Add launcher classes to the JAR file
6. Attach the created JAR file as an additional artifact to the project

**At run-time** (when you run `java -jar <YourProjectAndVersion>-pkg.jar`)

1. Start the launcher's main method
2. Read the JAR's manifest, extract information about the application's main class and dependencies
3. Register the custom `jij` (jar-in-jar) URL protocol
4. Create a single `URLClassLoader` for the classes contained directly in the JAR file and URLs to all dependencies
5. Register the classloader with the main thread
6. Call the application's main method

## Configuration

The *executable-packer-maven-plugin* supports most parameters that the *maven-jar-plugin* supports too. The following parameters can be specified in the `<configuration>` section:

| Parameter | Default value | Example | Description |
| --------- | ------------- | ------- | ----------- |
| **mainClass** | | `<mainClass>com.example.MyMainClass</mainClass>` | The class containing the `main` method to be run when executing the final JAR file. This is required. |
| **libPath** | lib | `<libPath>dependencies/libs</libPath>` | An (optional) subdirectory to put the libraries in. By default, "lib" is used. If the parameter is empty, the libraries will be packed into the root of the final JAR file. Nested subdirectories may be specified in the usual unix syntax. |
| classesDirectory | target/classes (`${project.build.outputDirectory}`) | `<classesDirectory>inputDir</classesDirectory>` | Directory containing the classes and resource files that should be packaged into the JAR. |
| includes | \*\*/\*\* | `<includes>**/**</includes>` | List of files to include from the classesDirectory. Specified as fileset patterns which are relative to the input directory whose contents is being packaged into the JAR. |
| excludes | \*\*/package.html | `<excludes>**/package.html</excludes>` | List of files to exclude from the classesDirectory. Specified as fileset patterns which are relative to the input directory whose contents is being packaged into the JAR. |
| outputDirectory | target (`${project.build.directory}`) | <outputDirectory>outputDir</outputDirectory> | Directory where the generated JAR should be saved in. |
| classifier | pkg | `<classifier>distpackage</classifier>` | Classifier to add to the artifact generated. For example, if the classifier is "pkg", the artifact will be named "(ProjectNameAndVersion)-pkg.jar". |
| archive | | | The archive configuration to use. See [Maven Archiver Reference](http://maven.apache.org/shared/maven-archiver/index.html). |
| forceCreation | false | `<forceCreation>true</forceCreation>` | Require the jar plugin to build a new JAR even if none of the contents appear to have changed. By default, this plugin looks to see if the output jar exists and inputs have not changed. If these conditions are true, the plugin skips creation of the jar. This does not work when other plugins, like the maven-shade-plugin, are configured to post-process the jar. This plugin can not detect the post-processing, and so leaves the post-processed jar in place. This can lead to failures when those plugins do not expect to find their own output as an input. Set this parameter to `true` to avoid these problems by forcing this plugin to recreate the jar every time. |

## Example

Complete `pom.xml`:

```
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<groupId>de.ntcomputer</groupId>
	<artifactId>executable-packer-maven-plugin-test</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<build>
		<plugins>
			<plugin>
				<artifactId>maven-compiler-plugin</artifactId>
				<version>3.5.1</version>
				<configuration>
					<source>1.8</source>
					<target>1.8</target>
				</configuration>
			</plugin>
			<plugin>
				<groupId>de.ntcomputer</groupId>
				<artifactId>executable-packer-maven-plugin</artifactId>
				<version>1.0.0</version>
				<configuration>
					<mainClass>de.ntcomputer.executablepacker.test.Test</mainClass>
				</configuration>
				<executions>
					<execution>
						<goals>
							<goal>pack-executable-jar</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
	<dependencies>
		<dependency>
			<groupId>com.examlpe</groupId>
			<artifactId>example-dependency</artifactId>
			<version>1.2.0</version>
		</dependency>
	</dependencies>
</project>
```

## Caveats

The runtime launcher registers the custom URL protocol `jij` in order to correctly resolve classes and resources. It sets a global `URLStreamHandlerFactory` to do so (using `URL.setURLStreamHandlerFactory()`).
If your application attempts to use `URL.setURLStreamHandlerFactory()` too, it will fail. Applications relying on setting a custom `URLStreamHandlerFactory` will not work in conjunction with this plugin.

## Credits

This plugin uses mostly the same parameters (including their descriptions) as the [maven-jar-plugin](https://maven.apache.org/plugins/maven-jar-plugin/).
The jar-in-jar classloader is inspired by the Eclipse [`jarinjarloader` package](http://git.eclipse.org/c/jdt/eclipse.jdt.ui.git/plain/org.eclipse.jdt.ui/jar%20in%20jar%20loader/org/eclipse/jdt/internal/jarinjarloader/) used by the *Export to Runnable JAR File* functionality.

## Alternatives

* [maven-assembly-plugin](http://maven.apache.org/plugins/maven-assembly-plugin/) with `jar-with-dependencies`
* [maven-shade-plugin](https://maven.apache.org/plugins/maven-shade-plugin/)
* [eclipse-jarinjarloader](https://github.com/raisercostin/eclipse-jarinjarloader)