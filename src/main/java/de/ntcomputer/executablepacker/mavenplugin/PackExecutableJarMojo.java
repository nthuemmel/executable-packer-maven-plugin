package de.ntcomputer.executablepacker.mavenplugin;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;

import de.ntcomputer.executablepacker.runtime.ExecutableLauncher;
import net.sf.corn.cps.CPScanner;
import net.sf.corn.cps.ClassFilter;

/**
 * Builds an executable JAR file from the current project, containing all dependency JAR files.
 * Supports most parameters that the maven-jar-plugin supports too.
 *
 * @author Nikolaus Thuemmel
 */
@Mojo(name = "pack-executable-jar", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true)
public class PackExecutableJarMojo extends AbstractMojo {
	private static final String[] DEFAULT_EXCLUDES = new String[] { "**/package.html" };
	private static final String[] DEFAULT_INCLUDES = new String[] { "**/**" };

	/**
	 * List of files to include. Specified as fileset patterns which are relative to the input directory whose contents
	 * is being packaged into the JAR.
	 */
	@Parameter
	private String[] includes;

	/**
	 * List of files to exclude. Specified as fileset patterns which are relative to the input directory whose contents
	 * is being packaged into the JAR.
	 */
	@Parameter
	private String[] excludes;

	/**
	 * Directory containing the generated JAR.
	 */
	@Parameter(defaultValue = "${project.build.directory}", required = true)
	private File outputDirectory;
	
	/**
     * Classifier to add to the artifact generated.
     * The artifact will be attached as a supplemental artifact.
     */
    @Parameter(defaultValue = "pkg", required = true)
    private String classifier;

	/**
	 * Name of the generated JAR (the classifier and .jar extension will be added to it, though).
	 */
	@Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
	private String finalName;

	/**
	 * The Jar archiver.
	 */
	@Component(role = Archiver.class, hint = "jar")
	private JarArchiver jarArchiver;

	/**
	 * The {@link {MavenProject}.
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;
	
	@Component
    private MavenProjectHelper projectHelper;

	/**
	 * The {@link MavenSession}.
	 */
	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	/**
	 * The archive configuration to use. See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven
	 * Archiver Reference</a>.
	 */
	@Parameter
	private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

	/**
	 * Require the jar plugin to build a new JAR even if none of the contents appear to have changed. By default, this
	 * plugin looks to see if the output jar exists and inputs have not changed. If these conditions are true, the
	 * plugin skips creation of the jar. This does not work when other plugins, like the maven-shade-plugin, are
	 * configured to post-process the jar. This plugin can not detect the post-processing, and so leaves the
	 * post-processed jar in place. This can lead to failures when those plugins do not expect to find their own output
	 * as an input. Set this parameter to <tt>true</tt> to avoid these problems by forcing this plugin to recreate the
	 * jar every time.<br/>
	 */
	@Parameter(property = "maven.jar.forceCreation", defaultValue = "false")
	private boolean forceCreation;

	/**
	 * Directory containing the classes and resource files that should be packaged into the JAR.
	 */
	@Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
	private File classesDirectory;

	/**
	 * An (optional) subdirectory to put the libraries in. By default, "lib" is used.
	 * If the parameter is empty, the libraries will be packed into the root of the final JAR file.
	 * Nested subdirectories may be specified in the usual unix syntax (e.g. "dependencies/libs")
	 */
	@Parameter(defaultValue = "lib", required = false)
	private String libPath;

	/**
	 * The class containing the main method to be run when executing the final JAR file.
	 */
	@Parameter(required = true)
	private String mainClass;

	private String[] getIncludes() {
		if (includes != null && includes.length > 0) {
			return includes;
		}
		return DEFAULT_INCLUDES;
	}

	private String[] getExcludes() {
		if (excludes != null && excludes.length > 0) {
			return excludes;
		}
		return DEFAULT_EXCLUDES;
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		// Old file-copy-based strategy. Not used anymore, but may be helpful as a reference in the future.
		/*
		// first create the directory to copy dependency libraries into
		File libraryDirectory = new File(outputDirectory, libPath);
		if (!libraryDirectory.isDirectory()) {
			getLog().debug("Creating library directory '" + libraryDirectory.getAbsolutePath() + "'");
			if (!libraryDirectory.mkdirs()) {
				throw new MojoFailureException("Failed to create library directory '" + libraryDirectory.getAbsolutePath() + "'");
			}
		} else {
			getLog().debug("Library directory '" + libraryDirectory.getAbsolutePath() + "' already exists");
		}

		String realLibPath = outputDirectory.toPath().relativize(libraryDirectory.toPath()).toString();
		getLog().info("Dependency JAR files will be placed in '" + realLibPath + "/'");

		// copy all relevant dependency JAR files to the library directory so they can be picked up by the archiver later
		Set<Artifact> dependencyArtifacts = project.getArtifacts();
		for (Artifact dependencyArtifact : dependencyArtifacts) {
			// include only compile-time and run-time dependencies
			if (Artifact.SCOPE_COMPILE.equals(dependencyArtifact.getScope()) || Artifact.SCOPE_RUNTIME.equals(dependencyArtifact.getScope())) {
				// include only JAR files
				if ("jar".equals(dependencyArtifact.getType())) {

					File dependencyFile = dependencyArtifact.getFile();
					getLog().info("Including dependency " + dependencyFile.getName());

					try {
						File destinationFile = new File(libraryDirectory, dependencyFile.getName());
						getLog().debug("Comparing dependency '" + dependencyFile.getAbsolutePath() + "' with '" + destinationFile.getAbsolutePath() + "'");

						// skip file if it exists and length and modification timestamp match
						if (destinationFile.isFile() && destinationFile.length() == dependencyFile.length() && destinationFile.lastModified() == dependencyFile.lastModified()) {
							getLog().debug("Skipped copying dependency '" + dependencyFile.getAbsolutePath() + "' to '" + destinationFile.getAbsolutePath() + "', file metadata matches");
						} else {
							getLog().debug("Copying dependency '" + dependencyFile.getAbsolutePath() + "' to '" + destinationFile.getAbsolutePath() + "'");
							FileUtils.copyFile(dependencyFile, destinationFile, true);
						}

					} catch (IOException e) {
						throw new MojoFailureException("Failed to copy dependency '" + dependencyFile.getAbsolutePath() + "' to '" + libraryDirectory.getAbsolutePath() + "'", e);
					}

				}
			}
		}
		*/
		
		// determine output file
		File outputJarFile = new File(outputDirectory, finalName + "-" + classifier + ".jar");
		getLog().debug("Creating JAR file at '" + outputJarFile.getAbsolutePath() + "'");
		
		// normalize library path
		String realLibPath = Paths.get(libPath).normalize().toString() + "/";
		getLog().info("Dependency JAR files will be placed in '" + realLibPath + "'");
		
		// build a list of all relevant dependency JAR files
		List<File> dependencyJarFiles = new ArrayList<File>();
		StringBuilder dependencyJarFileMetaStringBuilder = new StringBuilder();
		boolean dependencyJarFileFirst = true;
		for (Artifact dependencyArtifact: project.getArtifacts()) {
			// include only compile-time and run-time dependencies
			if (Artifact.SCOPE_COMPILE.equals(dependencyArtifact.getScope()) || Artifact.SCOPE_RUNTIME.equals(dependencyArtifact.getScope())) {
				// include only JAR files
				if ("jar".equals(dependencyArtifact.getType())) {
					File dependencyFile = dependencyArtifact.getFile();
					getLog().info("Including dependency " + dependencyFile.getName());
					dependencyJarFiles.add(dependencyFile);
					if(dependencyJarFileFirst) {
						dependencyJarFileFirst = false;
					} else {
						dependencyJarFileMetaStringBuilder.append("/");
					}
					dependencyJarFileMetaStringBuilder.append(dependencyFile.getName());
				}
			}
		}
		
		// create the archiver, set configuration (including dependencies and both launcher and application main class)
		MavenArchiver archiver = new MavenArchiver();
		archiver.setArchiver(jarArchiver);
		archiver.setOutputFile(outputJarFile);
		archive.setForced(forceCreation);
		archive.addManifestEntry("Main-Class", ExecutableLauncher.class.getName());
		archive.addManifestEntry(ExecutableLauncher.MANIFEST_APPLICATION_MAIN_CLASS, mainClass);
		archive.addManifestEntry(ExecutableLauncher.MANIFEST_DEPENDENCY_LIBPATH, realLibPath);
		archive.addManifestEntry(ExecutableLauncher.MANIFEST_DEPENDENCY_JARS, dependencyJarFileMetaStringBuilder.toString());

		try {
			// include all built classes
			if(classesDirectory.exists()) {
				getLog().debug("Including classes directory '" + classesDirectory.getAbsolutePath() + "'");
				archiver.getArchiver().addDirectory(classesDirectory, getIncludes(), getExcludes());
			} else {
				getLog().debug("Classes directory '" + classesDirectory.getAbsolutePath() + "' does not exist, not including in output JAR file");
			}
			
			// include all dependency JAR files
			for(File dependencyJarFile: dependencyJarFiles) {
				String destinationFilePath = realLibPath + dependencyJarFile.getName();
				getLog().debug("Including dependency JAR file '" + dependencyJarFile.getAbsolutePath() + "' as '" + destinationFilePath + "'");
				archiver.getArchiver().addFile(dependencyJarFile, destinationFilePath);
			}
			
			// include the executable launcher classes containing the classloader code
			List<Class<?>> launcherRuntimeClasses = CPScanner.scanClasses(new ClassFilter().packageName(ExecutableLauncher.class.getPackage().getName()));
			for(Class<?> runtimeClass: launcherRuntimeClasses) {
				try {
					String classFilePath = runtimeClass.getName().replace(".", "/") + ".class";
					URL classUrl = ExecutableLauncher.class.getClassLoader().getResource(classFilePath);
					if(classUrl==null) {
						throw new ArchiverException("Failed to resolve class file path '" + classFilePath + "' to a URL");
					}
					getLog().debug("Including launcher runtime class '" + classUrl + "' as '" + classFilePath + "'");
					URLResource resource = URLResource.create(classUrl);
					archiver.getArchiver().addResource(resource, classFilePath, archiver.getArchiver().getDefaultFileMode());
				} catch(Exception e) {
					throw new ArchiverException("Failed to include launcher class '" + runtimeClass.getName() + "' to the executable JAR file", e);
				}
			}

			// create JAR
			archiver.createArchive(session, project, archive);

		} catch (Exception e) {
			throw new MojoExecutionException("Error packing executable JAR file '" + outputJarFile.getAbsolutePath() + "'", e);
		}
		
		// attach built JAR to the project
		projectHelper.attachArtifact(project, "jar", classifier, outputJarFile);
	}

}
