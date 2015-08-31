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

package de.tudarmstadt.ukp.dkpro.argumentation.sequence.io;

import de.tudarmstadt.ukp.dkpro.argumentation.misc.uima.JCasUtil2;
import de.tudarmstadt.ukp.dkpro.argumentation.types.BIOSimplifiedSentenceArgumentAnnotation;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import de.tudarmstadt.ukp.dkpro.tc.api.io.TCReaderSequence;
import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationOutcome;
import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationSequence;
import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationUnit;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasDumpWriter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Main reader for the experiments in sequence labeling.
 *
 * @author Ivan Habernal
 */
public class ArgumentSequenceSentenceLevelReader
        extends XmiReader
        implements TCReaderSequence
{

    @Override
    public void getNext(CAS aCAS)
            throws IOException, CollectionException
    {
        super.getNext(aCAS);

        JCas jCas;
        try {
            jCas = aCAS.getJCas();
        }
        catch (CASException e) {
            throw new CollectionException(e);
        }

        // create text classification sequence
        TextClassificationSequence textClassificationSequence = new TextClassificationSequence(
                jCas);
        textClassificationSequence.setBegin(0);
        textClassificationSequence.setEnd(jCas.getDocumentText().length());
        textClassificationSequence.addToIndexes();

        Collection<BIOSimplifiedSentenceArgumentAnnotation> bioSimplifiedSentenceArgumentAnnotations = JCasUtil
                .select(jCas, BIOSimplifiedSentenceArgumentAnnotation.class);

        if (bioSimplifiedSentenceArgumentAnnotations.size() == 0) {
            try {
                SimplePipeline.runPipeline(aCAS,
                        AnalysisEngineFactory.createEngineDescription(CasDumpWriter.class));
            }
            catch (ResourceInitializationException | AnalysisEngineProcessException e) {
                throw new IOException(e);
            }

            throw new IllegalArgumentException(
                    "No BIOSimplifiedSentenceArgumentAnnotation annotations found.");
        }

        for (BIOSimplifiedSentenceArgumentAnnotation sentence : bioSimplifiedSentenceArgumentAnnotations) {

            String outcomeLabel = getTag(sentence);

            TextClassificationUnit unit = new TextClassificationUnit(jCas, sentence.getBegin(),
                    sentence.getEnd());
            unit.addToIndexes();

            TextClassificationOutcome outcome = new TextClassificationOutcome(jCas,
                    sentence.getBegin(), sentence.getEnd());
            outcome.setOutcome(outcomeLabel);
            outcome.addToIndexes();

            // make sure we have one-to-one sentence mapping
            List<Sentence> sentences = JCasUtil2.selectOverlapping(Sentence.class, outcome, jCas);
            if (sentences.size() != 1) {
                throw new IllegalArgumentException("Mapping from outcome to sentence is not 1:1");
            }
        }
    }

    protected String getTag(BIOSimplifiedSentenceArgumentAnnotation sentence)
    {
        return sentence.getTag();
    }

    @Override
    public String getTextClassificationOutcome(JCas jCas,
            TextClassificationUnit textClassificationUnit)
            throws CollectionException
    {
        return null;
    }
}
