/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ovirt.maven.plugin.jbossmodules;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.FileUtils;

/**
 * This mojo creates a {@code .zip} file containing the {@code .jar} file of the project and the {@code module.xml}
 * files available in the {@code src/main/modules} directory. This {@code .zip} file is then attached to the project
 * using {@code zip} as the type and a classifier composed by the optional {@code category} parameter and the
 * {@code modules} word.
 */
@Mojo(
    name = "attach-modules",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyCollection = ResolutionScope.COMPILE
)
@SuppressWarnings("unused")
public class AttachModulesMojo extends AbstractMojo {
    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * The project helper.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The name of the module.
     */
    @Parameter(property = "moduleName", required = false)
    private String moduleName;

    /**
     * The slot of the module.
     */
    @Parameter(property = "moduleSlot", required = false, defaultValue = "main")
    private String moduleSlot;

    /**
     * The list of modules to generate.
     */
    @Parameter(property = "modules")
    private List<Module> modules;

    /**
     * This is parameter is no longer used, index generation has been removed.
     */
    @Parameter(property = "generateIndex", defaultValue = "false")
    private boolean generateIndex;

    /**
     * Category of the module. If given the value is added as a prefix to the classifier and to the name of the
     * artifact. For example, if the value is {@code common} then the classifier will be {@code common-modules} and
     * the name of the attached artifact will be {@code common-modules.zip}.
     */
    @Parameter(property = "category", defaultValue="")
    private String category;

    /**
     * The temporary directory where modules will be stored.
     */
    private File modulesDir;

    public void execute() throws MojoExecutionException {
        // Do nothing if there isn't a source modules directory, this way the plugin can be executed in projects that
        // don't include modules::
        String sourcePath = "src" + File.separator + "main" + File.separator + "modules";
        File sourceDir = new File(project.getBasedir(), sourcePath);
        if (!sourceDir.exists()) {
            getLog().info(
                "The modules source directory \"" + sourceDir.getAbsolutePath() + "\" doesn't exist, no modules " +
                "artifacts will be attached."
            );
            return;
        }

        // Make sure the list of modules is not empty:
        if (modules == null) {
            modules = new ArrayList<Module>(1);
        }

        // If no modules have been explicitly given in the configuration then we assume that a module has to be created
        // for the current project, so we need to populate the module map and slot map with the value for the artifact
        // of this project:
        if (modules.isEmpty()) {
            Module module = new Module();
            module.setArtifactId(project.getArtifactId());
            module.setGroupId(project.getGroupId());
            modules.add(module);
        }

        // Locate the target directory:
        File targetDir = new File(project.getBuild().getDirectory());

        // Create the modules directory in the temporary maven directory:
        modulesDir = new File(targetDir, "modules");
        getLog().info("Creating modules directory \"" + modulesDir + "\"");
        if (!modulesDir.exists()) {
            if (!modulesDir.mkdirs()) {
                throw new MojoExecutionException(
                    "Can't create target modules directory \"" + modulesDir.getAbsolutePath() + "\""
                );
            }
        }

        // Copy any content from the source modules directory to the modules directory:
        getLog().info("Copying module resources to \"" + modulesDir + "\"");
        if (sourceDir.exists()) {
            try {
                FileUtils.copyDirectoryStructure(sourceDir,  modulesDir);
            }
            catch (IOException exception) {
                throw new MojoExecutionException(
                    "Can't copy source modules directory \"" + sourceDir.getAbsolutePath() + "\" to target modules " +
                    "directory \"" + modulesDir.getAbsolutePath() + "\"",
                    exception
                );
            }
        }

        // Generate the modules:
        for (Module module: modules) {
            createModule(module);
        }

        // Create the archive containing all the contents of the modules directory:
        File modulesArchive = new File(targetDir, makeArchiveName());
        ZipArchiver modulesArchiver = new ZipArchiver();
        modulesArchiver.setDestFile(modulesArchive);
        modulesArchiver.addDirectory(modulesDir);
        getLog().info("Creating module archive \"" + modulesArchive + "\"");
        try {
            modulesArchiver.createArchive();
        }
        catch (Exception exception) {
            throw new MojoExecutionException(
                "Can't generate modules archive \"" + modulesArchive.getAbsolutePath() + "\"",
                exception
            );
        }

        // Attach the generated zip file containing the modules as an additional artifact:
        getLog().info("Attaching modules artifact \"" + modulesArchive + "\"");
        projectHelper.attachArtifact(project, "zip", makeClassifier(), modulesArchive);
    }

    /**
     * Computes the name of the archive that contains the modules. It will be final name of the build, followed by
     * the optional category and then {@code modules.zip}, with dashes as separators where needed.
     */
    private String makeArchiveName() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(project.getBuild().getFinalName());
        buffer.append("-");
        if (category != null && !category.isEmpty()) {
            buffer.append(category);
            buffer.append("-");
        }
        buffer.append("modules.zip");
        return buffer.toString();
    }

    /**
     * Computes the classifier of the archive that contains the modules. It will be the optional category followed
     * by the word {@code modules}, with a dash as separator if needed.
     */
    private String makeClassifier() {
        StringBuilder buffer = new StringBuilder();
        if (category != null && !category.isEmpty()) {
            buffer.append(category);
            buffer.append("-");
        }
        buffer.append("modules");
        return buffer.toString();
    }

    private void createModule(Module module) throws MojoExecutionException {
        // Create the slot directory:
        String modulePath = module.getModuleName().replace(".", File.separator);
        String slotPath = modulePath + File.separator + module.getModuleSlot();
        File slotDir = new File(modulesDir, slotPath);
        getLog().info("Creating slot directory \"" + slotDir + "\"");
        if (!slotDir.exists()) {
            if (!slotDir.mkdirs()) {
                throw new MojoExecutionException(
                    "Can't create module directory \"" + slotDir.getAbsolutePath() + "\""
                );
            }
        }

        // Find the dependency with the same group and artifact id that the module:
        Artifact matchingArtifact = null;
        if (module.matches(project.getArtifact())) {
            matchingArtifact = project.getArtifact();
        }
        else {
            for (Artifact currentArtifact: project.getDependencyArtifacts()) {
                if (module.matches(currentArtifact)) {
                    matchingArtifact = currentArtifact;
               }
            }
        }
        if (matchingArtifact == null) {
            throw new MojoExecutionException(
                "Can't find dependency matching artifact id \"" + module.getArtifactId() + "\" and group " +
                "id \"" + module.getGroupId() + "\""
            );
        }

        // Copy the artifact to the slot directory:
        File artifactFrom = matchingArtifact.getFile();
        if (artifactFrom == null) {
            throw new MojoExecutionException(
                "Can't find file for artifact id \"" + module.getArtifactId() + "\" " + "and group id \"" +
                module.getGroupId() + "\""
            );
        }
        File artifactTo = new File(slotDir, module.getResourcePath());
        getLog().info("Copying artifact to \"" + artifactTo.getAbsolutePath() + "\"");
        try {
            FileUtils.copyFile(artifactFrom, artifactTo);
        }
        catch (IOException exception) {
            throw new MojoExecutionException(
                "Can't copy artifact file \"" + artifactFrom.getAbsolutePath() + "\" to slot directory \"" +
                slotDir.getAbsolutePath() + "\".",
                exception
            );
        }
    }
}
