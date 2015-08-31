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

import de.tudarmstadt.ukp.dkpro.argumentation.sequence.feature.deeplearning.EmbeddingFeatures;
import de.tudarmstadt.ukp.dkpro.argumentation.sequence.feature.lexical.LemmaLuceneNGramUFE;
import de.tudarmstadt.ukp.dkpro.argumentation.sequence.feature.meta.OrigBIOTokenSequenceMetaDataFeatureGenerator;
import de.tudarmstadt.ukp.dkpro.argumentation.sequence.feature.meta.OrigTokenSequenceMetaDataFeatureGenerator;
import de.tudarmstadt.ukp.dkpro.tc.svmhmm.util.OriginalTextHolderFeatureExtractor;

import java.util.*;

/**
 * (c) 2015 Ivan Habernal
 */
public class FeatureSetHelper
{
    public static Set<String> getRequiredMetaFeatures()
    {
        return new HashSet<>(Arrays.asList(OriginalTextHolderFeatureExtractor.class.getName(),
                OrigBIOTokenSequenceMetaDataFeatureGenerator.class.getName(),
                OrigTokenSequenceMetaDataFeatureGenerator.class.getName()));
    }

    public static Set<String> getBaselineFeatures()
    {
        Set<String> result = new HashSet<>();
        result.addAll(getRequiredMetaFeatures());

        result.addAll(Collections.singletonList(LemmaLuceneNGramUFE.class.getName()));

        return result;
    }

    public static Set<String> getEmbeddingFeatures()
    {
        Set<String> result = new HashSet<>();
        result.addAll(getRequiredMetaFeatures());

        result.addAll(Arrays.asList(
                // FS4
                EmbeddingFeatures.class.getName()));

        return result;
    }

    public static List<String> getFeatureSet(String featureSet)
    {
        Set<String> result = new HashSet<>();

        if (featureSet.contains("baseline")) {
            result.addAll(getBaselineFeatures());
        }

        if (featureSet.contains("embeddings")) {
            result.addAll(getEmbeddingFeatures());
        }

        return new ArrayList<>(result);
    }
}
