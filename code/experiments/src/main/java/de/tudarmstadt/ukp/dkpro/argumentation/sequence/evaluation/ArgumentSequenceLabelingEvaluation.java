/*
 * Copyright 2015
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
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

package de.tudarmstadt.ukp.dkpro.argumentation.sequence.evaluation;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import de.tudarmstadt.ukp.dkpro.argumentation.sequence.adapter.SVMAdapterBatchTokenReport;
import de.tudarmstadt.ukp.dkpro.argumentation.sequence.feature.lexical.LemmaLuceneNGramUFE;
import de.tudarmstadt.ukp.dkpro.argumentation.sequence.io.ArgumentSequenceSentenceLevelReader;
import de.tudarmstadt.ukp.dkpro.argumentation.sequence.report.TokenLevelEvaluationReport;
import de.tudarmstadt.ukp.dkpro.lab.Lab;
import de.tudarmstadt.ukp.dkpro.lab.task.Dimension;
import de.tudarmstadt.ukp.dkpro.lab.task.ParameterSpace;
import de.tudarmstadt.ukp.dkpro.lab.task.impl.BatchTask;
import de.tudarmstadt.ukp.dkpro.tc.core.Constants;
import de.tudarmstadt.ukp.dkpro.tc.fstore.simple.SparseFeatureStore;
import de.tudarmstadt.ukp.dkpro.tc.ml.ExperimentCrossValidation;
import de.tudarmstadt.ukp.dkpro.tc.svmhmm.task.SVMHMMTestTask;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.component.NoOpAnnotator;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.Arrays.asList;

/**
 * @author Ivan Habernal
 */
public class ArgumentSequenceLabelingEvaluation
{
    private static final int NUM_FOLDS = 5;

    @Parameter(names = { "--featureSet",
            "--fs" }, description = "Feature set name (baseline, embeddings)", required = true)
    String featureSet;

    @Parameter(names = { "--corpusPath",
            "--c" }, description = "Corpus path with XMI files", required = true)
    String corpusPath;

    @Parameter(names = { "--outputPath",
            "--o" }, description = "Main output path (folder)", required = true)
    String outputPath;

    public static void main(String[] args)
            throws Exception
    {
        ArgumentSequenceLabelingEvaluation evaluation = new ArgumentSequenceLabelingEvaluation();
        JCommander jCommander = new JCommander(evaluation, args);
        try {
            evaluation.run();
        }
        catch (ParameterException e) {
            e.printStackTrace();
            jCommander.usage();
        }

    }

    public void run()
            throws Exception
    {
        System.setProperty("org.apache.uima.logger.class",
                "org.apache.uima.util.impl.Log4jLogger_impl");

        File mainOutputFolder = new File(outputPath);
        // date
        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
                .format(new Date(System.currentTimeMillis()));
        File outputFolder = new File(mainOutputFolder, featureSet + "_" + date);

        outputFolder.mkdirs();

        System.setProperty("DKPRO_HOME", outputFolder.getAbsolutePath());

        // cross validation
        runCrossValidation(getParameterSpace());
    }

    public Map<String, Object> createDimReaders(String corpusFilePathTrain)
    {
        Map<String, Object> result = new HashMap<>();

        result.put(Constants.DIM_READER_TRAIN, ArgumentSequenceSentenceLevelReader.class);
        result.put(Constants.DIM_READER_TRAIN_PARAMS,
                Arrays.asList(ArgumentSequenceSentenceLevelReader.PARAM_SOURCE_LOCATION,
                        corpusFilePathTrain,
                        ArgumentSequenceSentenceLevelReader.PARAM_PATTERNS,
                        ArgumentSequenceSentenceLevelReader.INCLUDE_PREFIX + "*.xmi",
                        ArgumentSequenceSentenceLevelReader.PARAM_LENIENT, true));

        return result;
    }

    @SuppressWarnings("unchecked")
    public ParameterSpace getParameterSpace()
    {
        // configure training and test data reader dimension
        Map<String, Object> dimReaders = createDimReaders(corpusPath);

        Dimension<List<String>> dimFeatureSets = Dimension
                .create(Constants.DIM_FEATURE_SET, FeatureSetHelper.getFeatureSet(featureSet));

        // parameters to configure feature extractors
        Dimension<List<Object>> dimPipelineParameters = Dimension
                .create(Constants.DIM_PIPELINE_PARAMS, asList(new Object[] {
                                // top 50k ngrams
                                LemmaLuceneNGramUFE.PARAM_NGRAM_USE_TOP_K, "10000",
                                LemmaLuceneNGramUFE.PARAM_NGRAM_MIN_N, 1,
                                LemmaLuceneNGramUFE.PARAM_NGRAM_MAX_N, 3,
                        }
                ));

        // various orders of dependencies of transitions in HMM (max 3)
        Dimension<Integer> dimClassificationArgsT = Dimension
                .create(SVMHMMTestTask.PARAM_ORDER_T, 3);

        // various orders of dependencies of emissions in HMM (max 1)
        Dimension<Integer> dimClassificationArgsE = Dimension
                .create(SVMHMMTestTask.PARAM_ORDER_E, 1);

        // try different parametrization of C
        Dimension<Double> dimClassificationArgsC = Dimension.create(SVMHMMTestTask.PARAM_C, 5.0);

        return new ParameterSpace(Dimension.createBundle("readers", dimReaders),
                Dimension.create(Constants.DIM_LEARNING_MODE, Constants.LM_SINGLE_LABEL),
                Dimension.create(Constants.DIM_FEATURE_MODE, Constants.FM_SEQUENCE),
                Dimension.create(Constants.DIM_FEATURE_STORE, SparseFeatureStore.class.getName()),
                dimPipelineParameters, dimFeatureSets, dimClassificationArgsE,
                dimClassificationArgsT, dimClassificationArgsC);
    }

    public void runCrossValidation(ParameterSpace pSpace)
            throws Exception
    {
        ExperimentCrossValidation batch = new ExperimentCrossValidation(
                "ArgumentSequenceLabelingCV", SVMAdapterBatchTokenReport.class, getPreprocessing(),
                NUM_FOLDS);
        batch.setParameterSpace(pSpace);
        batch.addInnerReport(TokenLevelEvaluationReport.class);
        batch.setExecutionPolicy(BatchTask.ExecutionPolicy.RUN_AGAIN);

        // Run
        Lab.getInstance().run(batch);
    }

    protected AnalysisEngineDescription getPreprocessing()
            throws ResourceInitializationException
    {
        return AnalysisEngineFactory.createEngineDescription(NoOpAnnotator.class);
    }
}
