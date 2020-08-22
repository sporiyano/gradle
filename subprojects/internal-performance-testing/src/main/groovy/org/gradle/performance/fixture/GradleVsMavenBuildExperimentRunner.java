/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.UncheckedException;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.profiler.BenchmarkResultCollector;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutatorFactory;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.MavenScenarioDefinition;
import org.gradle.profiler.MavenScenarioInvoker;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.report.CsvGenerator;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GradleVsMavenBuildExperimentRunner extends GradleProfilerBuildExperimentRunner {
    public GradleVsMavenBuildExperimentRunner(BenchmarkResultCollector resultCollector) {
        super(resultCollector);
    }

    @Override
    public void doRun(BuildExperimentSpec experiment, MeasuredOperationList results) {
//        super.run(exp, results);
//        InvocationSpec invocation = exp.getInvocation();
        if (experiment instanceof MavenBuildExperimentSpec) {
//            List<String> additionalJvmOpts = getProfiler().getAdditionalJvmOpts(exp);
//            MavenInvocationSpec mavenInvocationSpec = (MavenInvocationSpec) invocation;
//            mavenInvocationSpec = mavenInvocationSpec.withBuilder().jvmOpts(additionalJvmOpts).build();
//            MavenBuildExperimentSpec experiment = (MavenBuildExperimentSpec) exp;
            runMavenExperiment((MavenBuildExperimentSpec) experiment, results);
        } else {
            super.doRun(experiment, results);
        }
    }

    private void runMavenExperiment(MavenBuildExperimentSpec experimentSpec, MeasuredOperationList results) {
        MavenInvocationSpec invocationSpec = experimentSpec.getInvocation();
        File workingDirectory = invocationSpec.getWorkingDirectory();

        InvocationSettings invocationSettings = createInvocationSettings(experimentSpec);
        MavenScenarioDefinition scenarioDefinition = createScenarioDefinition(experimentSpec, invocationSettings);

        try {
            MavenScenarioInvoker scenarioInvoker = new MavenScenarioInvoker();
            AtomicInteger iterationCount = new AtomicInteger(0);
            int warmUpCount = scenarioDefinition.getWarmUpCount();
            Logging.setupLogging(workingDirectory);

            Consumer<BuildInvocationResult> scenarioReporter = resultCollector.scenario(
                scenarioDefinition,
                ImmutableList.<Sample<? super BuildInvocationResult>>builder()
                    .add(BuildInvocationResult.EXECUTION_TIME)
                    .build()
            );
            scenarioInvoker.run(scenarioDefinition, invocationSettings, new BenchmarkResultCollector() {
                @Override
                public <T extends BuildInvocationResult> Consumer<T> scenario(ScenarioDefinition scenario, List<Sample<? super T>> samples) {
                    return invocationResult -> {
                        int currentIteration = iterationCount.incrementAndGet();
                        if (currentIteration > warmUpCount) {
                            MeasuredOperation measuredOperation = new MeasuredOperation();
                            measuredOperation.setTotalTime(Duration.millis(invocationResult.getExecutionTime().toMillis()));
                            results.add(measuredOperation);
                        }
                        scenarioReporter.accept(invocationResult);
                    };
                }
            });
            flameGraphGenerator.generateGraphs(experimentSpec);
            flameGraphGenerator.generateDifferentialGraphs();
        } catch (IOException | InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            try {
                Logging.resetLogging();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private InvocationSettings createInvocationSettings(MavenBuildExperimentSpec experimentSpec) {
        File outputDir = flameGraphGenerator.getJfrOutputDirectory(experimentSpec);
        return new InvocationSettings(
            experimentSpec.getInvocation().getWorkingDirectory(),
            profiler,
            true,
            outputDir,
            new BuildInvoker() {
                @Override
                public String toString() {
                    return "Maven";
                }
            },
            false,
            null,
            ImmutableList.of(experimentSpec.getInvocation().getMavenVersion()),
            experimentSpec.getInvocation().getTasksToRun(),
            ImmutableMap.of(),
            null,
            warmupsForExperiment(experimentSpec),
            invocationsForExperiment(experimentSpec),
            false,
            ImmutableList.of(),
            CsvGenerator.Format.LONG);
    }

    private MavenScenarioDefinition createScenarioDefinition(MavenBuildExperimentSpec experimentSpec, InvocationSettings invocationSettings) {
        return new MavenScenarioDefinition(
            experimentSpec.getDisplayName(),
            experimentSpec.getDisplayName(),
            experimentSpec.getInvocation().getTasksToRun(),
            new BuildMutatorFactory(experimentSpec.getBuildMutators().stream()
                .map(mutatorFunction -> toMutatorSupplierForSettings(invocationSettings, mutatorFunction))
                .collect(Collectors.toList())
            ),
            invocationSettings.getWarmUpCount(),
            invocationSettings.getBuildCount(),
            invocationSettings.getOutputDir()
        );
    }
//
//
//    private void runMavenExperiment(MeasuredOperationList results, final MavenBuildExperimentSpec experiment, final MavenInvocationSpec buildSpec) {
//        File projectDir = buildSpec.getWorkingDirectory();
//        performMeasurements((invocationInfo, invocationCustomizer) -> measuredOperation -> {
//            System.out.println("Run Maven using JVM opts: " + Iterables.concat(buildSpec.getMavenOpts(), buildSpec.getJvmOpts()));
//            List<String> cleanTasks = buildSpec.getCleanTasks();
//            if (!cleanTasks.isEmpty()) {
//                System.out.println("Cleaning up by running Maven tasks: " + Joiner.on(" ").join(buildSpec.getCleanTasks()));
//                ExecAction clean = createMavenInvocation(buildSpec, cleanTasks);
//                executeWithFileLogging(experiment, clean);
//            }
//
//            MavenInvocationSpec invocation = invocationCustomizer.customize(invocationInfo, buildSpec);
//            final ExecAction run = createMavenInvocation(invocation, invocation.getTasksToRun());
//            System.out.println("Measuring Maven tasks: " + Joiner.on(" ").join(buildSpec.getTasksToRun()));
//            DurationMeasurementImpl.measure(measuredOperation, () -> executeWithFileLogging(experiment, run));
//        }, experiment, results, projectDir);
//    }
//
//    private void executeWithFileLogging(MavenBuildExperimentSpec experiment, ExecAction mavenInvocation) {
//        File log = new File(experiment.getWorkingDirectory(), "log.txt");
//        OutputStream out = null;
//        try {
//            out = new FileOutputStream(log, true);
//            mavenInvocation.setErrorOutput(out);
//            mavenInvocation.setStandardOutput(out);
//            mavenInvocation.execute();
//        } catch (IOException e) {
//            throw new UncheckedIOException(e);
//        } finally {
//            CompositeStoppable.stoppable(out).stop();
//        }
//    }
//
//    @Override
//    protected InvocationCustomizer createInvocationCustomizer(final BuildExperimentInvocationInfo info) {
//        if (info.getBuildExperimentSpec() instanceof MavenBuildExperimentSpec) {
//            return new InvocationCustomizer() {
//                @Override
//                public InvocationSpec customize(BuildExperimentInvocationInfo info, InvocationSpec invocationSpec) {
//                    final List<String> iterationInfoArguments = createIterationInfoArguments(info.getPhase(), info.getIterationNumber(), info.getIterationMax());
//                    return ((MavenInvocationSpec) invocationSpec).withBuilder().args(iterationInfoArguments).build();
//                }
//            };
//        }
//        return super.createInvocationCustomizer(info);
//    }
//
//    private ExecAction createMavenInvocation(MavenInvocationSpec buildSpec, List<String> tasks) {
//        ExecAction execAction = execActionFactory.newExecAction();
//        execAction.setWorkingDir(buildSpec.getWorkingDirectory());
//        execAction.executable(buildSpec.getInstallation().getMvn());
//        execAction.args(buildSpec.getArgs());
//        execAction.args(tasks);
//        execAction.environment("MAVEN_OPTS", Joiner.on(' ').join(Iterables.concat(buildSpec.getMavenOpts(), buildSpec.getJvmOpts())));
//        return execAction;
//    }
}
