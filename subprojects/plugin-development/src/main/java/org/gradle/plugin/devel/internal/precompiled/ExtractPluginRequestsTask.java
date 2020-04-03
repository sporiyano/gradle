/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.devel.internal.precompiled;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.ScriptCompilationHandler;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Actions;
import org.gradle.plugin.use.internal.PluginsAwareScript;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
abstract class ExtractPluginRequestsTask extends DefaultTask {

    private final ScriptCompilationHandler scriptCompilationHandler;
    private final CompileOperationFactory compileOperationFactory;
    private final ClassLoaderScope classLoaderScope;

    @Inject
    public ExtractPluginRequestsTask(ScriptCompilationHandler scriptCompilationHandler,
                                     ClassLoaderScopeRegistry classLoaderScopeRegistry,
                                     CompileOperationFactory compileOperationFactory,
                                     List<PrecompiledGroovyScript> scriptPlugins) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.compileOperationFactory = compileOperationFactory;
        this.classLoaderScope = classLoaderScopeRegistry.getCoreAndPluginsScope();
    }

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    Set<File> getScriptFiles() {
        return getScriptPlugins().get().stream().map(p -> p.getSource().getResource().getFile()).collect(Collectors.toSet());
    }

    @OutputDirectory
    abstract DirectoryProperty getExtractedPluginRequestsClassesDir();

    @Internal
    abstract ListProperty<PrecompiledGroovyScript> getScriptPlugins();

    @TaskAction
    void extractPluginsBlocks() {
        for (PrecompiledGroovyScript scriptPlugin : getScriptPlugins().get()) {
            compilePluginsBlock(scriptPlugin);
        }
    }

    private void compilePluginsBlock(PrecompiledGroovyScript scriptPlugin) {
        CompileOperation<?> pluginsCompileOperation = compileOperationFactory.getPluginsBlockCompileOperation(scriptPlugin.getScriptTarget());
        File outputDir = getExtractedPluginRequestsClassesDir().get().dir(scriptPlugin.getId()).getAsFile();
        scriptCompilationHandler.compileToDir(
            scriptPlugin.getSource(), classLoaderScope.getExportClassLoader(), outputDir, outputDir, pluginsCompileOperation,
            PluginsAwareScript.class, Actions.doNothing());
    }

}
