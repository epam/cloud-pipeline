package com.epam.pipeline.manager.preprocessing;

import com.epam.pipeline.entity.samplesheet.SampleSheet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class SampleSheetParserTest {

    private static final String VALID_SAMPLE_SHEET = "[Header]\n" +
            "Date,2017-04-05\n" +
            "Workflow,Test\n" +
            "Application,Test Amplicon\n" +
            "Assay,Test Amplicon\n" +
            "Description,Chemistry,Amplicon\n" +
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

    private final static String VALID_SAMPLE_SHEET_WITH_COMMAS = "[Header],,,,\n" +
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
            "outputgenomevcf,FALSE,,,,\n" +
            "[Data],,,,\n" +
            "Sample_ID,Sample_Name,II7_Index_ID,index,II5_Index_ID,index2,Manifest,GenomeFolder,,,,\n" +
            "A100201,Sample_A,A1,ATCACGAAC,A1,TGAACCTTT,A,test\\WholeGenomeFasta,,,,\n" +
            "A100302,Sample_B,A2,ACAGTGCGT,A1,TGAACCTTT,A,test\\WholeGenomeFasta,,,,\n" +
            "A100503,Sample_C,A3,CAGATCGCA,A1,TGATACCTT,B,testb\\WholeGenomeFasta,,,,\n" +
            "A100104,Sample_D,A4,ACAAACGAG,A1,TGAACCTTT,B,testb\\WholeGenomeFasta,,,,";

    @Parameterized.Parameters
    public static Collection<Object[]> validSampleSheets() {
        return Arrays.asList(new Object[][] {
                { VALID_SAMPLE_SHEET }, { VALID_SAMPLE_SHEET_WITH_COMMAS }
        });
    }

    private final String sampleSheetContent;

    public SampleSheetParserTest(String sampleSheetContent) {
        this.sampleSheetContent = sampleSheetContent;
    }

    @Test
    public void parseValidSampleSheetTest() {
        SampleSheet sampleSheet = SampleSheetParser.parseSampleSheet(
                sampleSheetContent.getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(sampleSheet.isWithHeader());
        Assert.assertTrue(sampleSheet.isWithData());
        Assert.assertFalse(sampleSheet.getDataLines().isEmpty());
    }

}