package com.github.kkkubakkk.gradledependenciesplugin.actions

import com.github.kkkubakkk.gradledependenciesplugin.dataClasses.Dependencies
import com.github.kkkubakkk.gradledependenciesplugin.dataClasses.Dependency
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.guessProjectDir
import io.ktor.utils.io.errors.*

class GradleDependenciesImporterAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            e.presentation.isVisible = true
            return
        }

        val buildFile = findBuildFile(project)
        e.presentation.isEnabled = buildFile != null
        e.presentation.isVisible = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.name.endsWith(".txt") }
            .withTitle("Select Dependencies File")
            .withDescription("Select a .txt file with dependencies in JSON format")

        try {
            val jsonFile = FileChooser.chooseFile(descriptor, project, null)
            if (jsonFile == null) {
                Messages.showMessageDialog(project, "No file selected", "Error", Messages.getErrorIcon())
                return
            }

            val jsonContent = jsonFile.inputStream.bufferedReader().use { it.readText() }

            try {
                val dependencies = parseDependencies(jsonContent)
                val buildFile = findBuildFile(project) ?: throw IOException("Build file not found")
                updateBuildFile(project, buildFile, dependencies)
                Messages.showInfoMessage(project, "Dependencies successfully imported", "Success")
            } catch (e: JsonSyntaxException) {
                Messages.showMessageDialog(
                    project,
                    "Invalid JSON format: ${e.message}",
                    "Error",
                    Messages.getErrorIcon()
                )
            }
        } catch (e: IOException) {
            Messages.showMessageDialog(project, "Error reading file: ${e.message}", "Error", Messages.getErrorIcon())
        } catch (e: Exception) {
            Messages.showMessageDialog(
                project,
                "Unexpected error occurred: ${e.message}",
                "Error",
                Messages.getErrorIcon()
            )
        }
    }

    private fun parseDependencies(jsonContent: String): Dependencies =
        Gson().fromJson(jsonContent, object : TypeToken<Dependencies>() {}.type)

    private fun findBuildFile(project: Project): VirtualFile? {
        val projectDir = project.guessProjectDir() ?: return null
        return projectDir.findChild("build.gradle.kts") ?: projectDir.findChild("build.gradle")
    }
    
    private fun updateBuildFile(project: Project, buildFile: VirtualFile?, dependencies: Dependencies) {
        if (buildFile == null) {
            Messages.showMessageDialog(project, "No build file found", "Error", Messages.getErrorIcon())
            return
        }

        val document = FileDocumentManager.getInstance().getDocument(buildFile) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            val content = document.text
            val newContent = insertDependencies(content, dependencies)
            document.setText(newContent)
        }
    }

    private fun insertDependencies(buildFileContent: String, dependencies: Dependencies): String {
        val contentBuilder = StringBuilder(buildFileContent)

        val depsBlockStart = buildFileContent.indexOf("dependencies {")

        if (depsBlockStart == -1) {
            contentBuilder.append("\ndependencies {\n")
            dependencies.dependencies.forEach { dep ->
                contentBuilder.append(createDependencyString(dep))
            }
            contentBuilder.append("}")
        } else {
            val insertPosition = buildFileContent.indexOf("{", depsBlockStart) + 1
            val dependenciesText = dependencies.dependencies.joinToString("\n") { createDependencyString(it) }
            contentBuilder.insert(insertPosition, "\n$dependenciesText")
        }

        return contentBuilder.toString()
    }

    private fun createDependencyString(dependency: Dependency): String =
        "    ${dependency.configuration}(\"${dependency.group}:${dependency.name}:${dependency.version}\")\n"
}