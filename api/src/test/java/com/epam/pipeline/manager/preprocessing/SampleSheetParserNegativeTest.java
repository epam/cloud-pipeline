/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.preprocessing;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class SampleSheetParserNegativeTest {

    private static final String SAMPLE_SHEET_WITHOUT_HEADER =
            "[Manifests]\n" +
            "A,TestSeqAmpliconManifest-1.txt\n" +
            "B,TestSeqAmpliconManifest-2.txt\n" +
            "[Reads]\n" +
            "135\n" +
            "135\n" +
            "[Settings]\n" +
            "VariantFilterQualityCutoff,30\n" +
            "outputgenomevcf,FALSE\n" +
            "[Data]\n" +
            "Sample_ID,Sample_Name,II7_Index_ID,index,II5_Index_ID,index2,Manifest,GenomeFolder\n" +
            "A100201,Sample_A,A1,ATCACGAAC,A1,TGAACCTTT,A,test\\WholeGenomeFasta\n" +
            "A100302,Sample_B,A2,ACAGTGCGT,A1,TGAACCTTT,A,test\\WholeGenomeFasta\n" +
            "A100503,Sample_C,A3,CAGATCGCA,A1,TGATACCTT,B,testb\\WholeGenomeFasta\n" +
            "A100104,Sample_D,A4,ACAAACGAG,A1,TGAACCTTT,B,testb\\WholeGenomeFasta";

    private static final String SAMPLE_SHEET_WITHOUT_DATA_LINES = "[Header],,,,\n" +
            "Date,2017-04-05,,,,\n" +
            "Workflow,Test,,,,\n" +
            "Application,Test Amplicon,,,,\n" +
            "Assay,Test Amplicon,,,,\n" +
            "Description,Chemistry,Amplicon,,,,\n" +
            "[Manifests],,,,\n" +
            "A,TestSeqAmpliconManifest-1.txt,,,,\n" +
            "B,TestSeqAmpliconManifest-2.txt,,,,\n" +
            "[Reads],,,,\n" +
            "135,,,,\n" +
            "135,,,,\n" +
            "[Settings],,,,\n" +
            "VariantFilterQualityCutoff,30,,,,\n" +
            "outputgenomevcf,FALSE,,,,\n";

    private static final String NON_SAMPLE_SHEET_CONTENT = "Hi Mom!\n I'm a programmer!\n";

    @Parameterized.Parameters
    public static Collection<Object[]> validSampleSheets() {
        return Arrays.asList(new Object[][] {
                { SAMPLE_SHEET_WITHOUT_DATA_LINES }, { SAMPLE_SHEET_WITHOUT_HEADER }, { NON_SAMPLE_SHEET_CONTENT }
        });
    }

    private final String sampleSheetContent;

    public SampleSheetParserNegativeTest(final String sampleSheetContent) {
        this.sampleSheetContent = sampleSheetContent;
    }

    @Test(expected = IllegalStateException.class)
    public void failToParseSampleSheetWithoutDataLines() {
        SampleSheetParser.parseSampleSheet(sampleSheetContent.getBytes(StandardCharsets.UTF_8));
    }

}
