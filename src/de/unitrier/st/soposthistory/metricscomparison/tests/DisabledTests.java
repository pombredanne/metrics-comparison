package de.unitrier.st.soposthistory.metricscomparison.tests;

import de.unitrier.st.soposthistory.metricscomparison.evaluation.MetricEvaluationManager;
import de.unitrier.st.soposthistory.metricscomparison.evaluation.MetricEvaluationPerPost;
import de.unitrier.st.soposthistory.metricscomparison.evaluation.MetricResult;
import de.unitrier.st.soposthistory.metricscomparison.evaluation.SimilarityMetric;
import de.unitrier.st.soposthistory.metricscomparison.statistics.Statistics;
import de.unitrier.st.soposthistory.version.PostVersionList;
import de.unitrier.st.util.Util;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import static de.unitrier.st.soposthistory.metricscomparison.statistics.Statistics.rootPathToGTSamples;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
class DisabledTests {
    private static Logger logger;

    private static Path pathToOldMetricComparisonResults = Paths.get(
            "testdata", "metrics_comparison", "results_metric_comparison_old.csv"
    );

    static {
        try {
            logger = Util.getClassLogger(de.unitrier.st.soposthistory.metricscomparison.tests.DisabledTests.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void compareMetricEvaluationManagerWithOldProjectTest() {
        MetricEvaluationManager manager = MetricEvaluationManager.DEFAULT
                .withName("TestCompareWithOldProject")
                .withInputPaths(MetricEvaluationTest.pathToPostIdList, MetricEvaluationTest.pathToPostHistory,
                        MetricEvaluationTest.pathToGroundTruth)
                .withOutputDirPath(MetricEvaluationTest.testOutputDir)
                .withNumberOfRepetitions(1)
                .initialize();

        List<Integer> postHistoryIds_3758880 = manager.getPostVersionLists().get(3758880).getPostHistoryIds();
        List<Integer> postHistoryIds_22037280 = manager.getPostVersionLists().get(22037280).getPostHistoryIds();

        Set<String> excludedVariants = new HashSet<>();
        excludedVariants.add("Kondrak05");

        CSVParser csvParser;

        Thread managerThread = new Thread(manager);
        managerThread.start();
        try {
            managerThread.join();

            csvParser = CSVParser.parse(
                    pathToOldMetricComparisonResults.toFile(),
                    StandardCharsets.UTF_8,
                    MetricEvaluationManager.csvFormatMetricEvaluationPerVersion.withFirstRecordAsHeader()
            );

            csvParser.getHeaderMap();
            List<CSVRecord> records = csvParser.getRecords();
            for (CSVRecord record : records) {
                String metric = record.get("metric");

                boolean skipRecord = false;
                for(String excludedVariant : excludedVariants) {
                    if (metric.contains(excludedVariant)) {
                        skipRecord = true;
                        break;
                    }
                }

                if(skipRecord) {
                    continue;
                }

                Double threshold = Double.valueOf(record.get("threshold"));
                // comparison manager computes only thresholds mod 0.10 by now so unequal thresholds will be skipped
                if ((int) (threshold * 100) % 10 != 0) {
                    continue;
                }

                Integer postId = Integer.valueOf(record.get("postid"));

                Integer postHistoryId = null;

                Integer truePositivesText = null;
                Integer trueNegativesText = null;
                Integer falsePositivesText = null;
                Integer falseNegativesText = null;

                Integer truePositivesCode = null;
                Integer trueNegativesCode = null;
                Integer falsePositivesCode = null;
                Integer falseNegativesCode = null;

                try {
                    postHistoryId = Integer.valueOf(record.get("posthistoryid"));

                    truePositivesText = Integer.valueOf(record.get("#truepositivestext"));
                    trueNegativesText = Integer.valueOf(record.get("#truenegativestext"));
                    falsePositivesText = Integer.valueOf(record.get("#falsepositivestext"));
                    falseNegativesText = Integer.valueOf(record.get("#falsenegativestext"));

                    truePositivesCode = Integer.valueOf(record.get("#truepositivescode"));
                    trueNegativesCode = Integer.valueOf(record.get("#truenegativescode"));
                    falsePositivesCode = Integer.valueOf(record.get("#falsepositivescode"));
                    falseNegativesCode = Integer.valueOf(record.get("#falsenegativescode"));
                } catch (NumberFormatException ignored) {
                }

                MetricEvaluationPerPost tmpMetricEvaluationPerPost = manager.getMetricEvaluation(postId, metric, threshold);

                if (postHistoryId == null) {
                    List<Integer> postHistoryIds;
                    if (postId == 3758880) {
                        postHistoryIds = postHistoryIds_3758880;
                    } else if (postId == 22037280) {
                        postHistoryIds = postHistoryIds_22037280;
                    } else {
                        throw new IllegalArgumentException("Post with id " + postId + " has not been listed in test set");
                    }


                    boolean resultsTextNull = false;
                    boolean resultsCodeNull = false;
                    assertNotNull(postHistoryIds);
                    for (Integer tmpPostHistoryId : postHistoryIds) {
                        MetricResult resultsText = tmpMetricEvaluationPerPost.getResultsText(tmpPostHistoryId);
                        MetricResult resultsCode = tmpMetricEvaluationPerPost.getResultsCode(tmpPostHistoryId);

                        // in previous versions, the results were set to null in case one comparison failed
                        resultsTextNull = resultsTextNull || resultsText.getFailedPredecessorComparisons() > 0;
                        resultsCodeNull = resultsCodeNull || resultsCode.getFailedPredecessorComparisons() > 0;
                    }
                    assertTrue(resultsTextNull || resultsCodeNull);
                } else {
                    MetricResult resultsText = tmpMetricEvaluationPerPost.getResultsText(postHistoryId);
                    assertEquals(truePositivesText, Integer.valueOf(resultsText.getTruePositives()));
                    assertEquals(trueNegativesText, Integer.valueOf(resultsText.getPostBlockVersionCount() - resultsText.getTruePositives() - resultsText.getFalsePositives() - resultsText.getFalseNegatives()));
                    assertEquals(trueNegativesText, Integer.valueOf(resultsText.getPostBlockVersionCount() - resultsText.getTruePositives() - resultsText.getFalseNegatives()));
                    assertEquals(falsePositivesText, Integer.valueOf(resultsText.getFalsePositives()));
                    assertEquals(falseNegativesText, Integer.valueOf(resultsText.getFalseNegatives()));

                    MetricResult resultsCode = tmpMetricEvaluationPerPost.getResultsCode(postHistoryId);
                    assertEquals(truePositivesCode, Integer.valueOf(resultsCode.getTruePositives()));
                    assertEquals(trueNegativesCode, Integer.valueOf(resultsCode.getPostBlockVersionCount() - resultsCode.getTruePositives() - resultsCode.getFalsePositives() - resultsCode.getFalseNegatives()));
                    assertEquals(trueNegativesCode, Integer.valueOf(resultsCode.getPostBlockVersionCount() - resultsCode.getTruePositives() - resultsCode.getFalseNegatives()));
                    assertEquals(falsePositivesCode, Integer.valueOf(resultsCode.getFalsePositives()));
                    assertEquals(falseNegativesCode, Integer.valueOf(resultsCode.getFalseNegatives()));
                }
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testGTSamplesParsable() {
        testSamples(Statistics.pathsToGTSamples);
    }

    @Test
    void testTestSamplesParsable() {
        testSamples(Statistics.pathsToTestSamples);
    }

    private void testSamples(List<Path> samplePaths) {
        for (Path currentSamplePath : samplePaths) {
            Path currentSampleFiles = Paths.get(currentSamplePath.toString(), "files");

            File[] postHistoryFiles = currentSampleFiles.toFile().listFiles(
                    (dir, name) -> name.matches(PostVersionList.fileNamePattern.pattern())
            );

            assertNotNull(postHistoryFiles);

            for (File postHistoryFile : postHistoryFiles) {
                Matcher fileNameMatcher = PostVersionList.fileNamePattern.matcher(postHistoryFile.getName());
                if (fileNameMatcher.find()) {
                    int postId = Integer.parseInt(fileNameMatcher.group(1));
                    // no exception should be thrown for the following two lines
                    PostVersionList postVersionList = PostVersionList.readFromCSV(currentSampleFiles, postId, -1);
                    postVersionList.normalizeLinks();
                    assertTrue(postVersionList.size() > 0);
                }
            }
        }
    }

    @Test
    void comparePossibleMultipleConnectionsWithOldProjectTest() {
        // This test case "fails" because the extraction of post blocks has been changed since the creation of the old file.

        File oldFile = Paths.get(Statistics.pathToMultipleConnectionsDir.toString(),
                "multiple_possible_connections_old.csv").toFile();
        File newFile = Statistics.pathToMultipleConnectionsFile.toFile();

        CSVParser csvParserOld, csvParserNew;
        try {
            // parse old records
            csvParserOld = CSVParser.parse(
                    oldFile,
                    StandardCharsets.UTF_8,
                    Statistics.csvFormatMultipleConnections.withFirstRecordAsHeader()
                            .withHeader("postId", "postHistoryId", "localId", "blockTypeId",
                                    "possiblePredOrSuccLocalIds", "numberOfPossibleSuccessorsOrPredecessors")
            );
            List<CSVRecord> oldRecords = csvParserOld.getRecords();
            List<MultipleConnectionsResultOld> oldResults = new ArrayList<>(oldRecords.size());

            for (CSVRecord record : oldRecords) {
                int postId = Integer.parseInt(record.get("postId"));
                int postHistoryId = Integer.parseInt(record.get("postHistoryId"));
                int localId = Integer.parseInt(record.get("localId"));
                int postBlockTypeId = Integer.parseInt(record.get("blockTypeId"));
                String possiblePredOrSuccLocalIds = record.get("possiblePredOrSuccLocalIds");
                int numberOfPossibleSuccessorsOrPredecessors = Integer.parseInt(record.get("numberOfPossibleSuccessorsOrPredecessors"));

                oldResults.add(new MultipleConnectionsResultOld(postId, postHistoryId, localId, postBlockTypeId,
                        possiblePredOrSuccLocalIds, numberOfPossibleSuccessorsOrPredecessors));
            }

            // parse new records
            csvParserNew = CSVParser.parse(
                    newFile,
                    StandardCharsets.UTF_8,
                    Statistics.csvFormatMultipleConnections.withFirstRecordAsHeader()
            );

            List<CSVRecord> newRecords = csvParserNew.getRecords();
            List<MultipleConnectionsResultNew> newResults = new ArrayList<>(newRecords.size());

            for (CSVRecord record : newRecords) {
                int postId = Integer.parseInt(record.get("PostId"));
                int postHistoryId = Integer.parseInt(record.get("PostHistoryId"));
                int localId = Integer.parseInt(record.get("LocalId"));
                int postBlockTypeId = Integer.parseInt(record.get("PostBlockTypeId"));
                int possiblePredecessorsCount = Integer.parseInt(record.get("PossiblePredecessorsCount"));
                int possibleSuccessorsCount = Integer.parseInt(record.get("PossibleSuccessorsCount"));
                String possiblePredecessorLocalIds = record.get("PossiblePredecessorLocalIds");
                String possibleSuccessorLocalIds = record.get("PossibleSuccessorLocalIds");

                newResults.add(new MultipleConnectionsResultNew(postId, postHistoryId, localId, postBlockTypeId,
                        possiblePredecessorsCount, possibleSuccessorsCount,
                        possiblePredecessorLocalIds, possibleSuccessorLocalIds));
            }

            // compare old and new results
            for (MultipleConnectionsResultNew multipleConnectionsResultNew : newResults) {
                int newPostId = multipleConnectionsResultNew.postId;
                int newPostHistoryId = multipleConnectionsResultNew.postHistoryId;
                int newLocalId = multipleConnectionsResultNew.localId;

                int newPostBlockTypeId = multipleConnectionsResultNew.postBlockTypeId;
                int newPossiblePredecessorsCount = multipleConnectionsResultNew.possiblePredecessorsCount;
                int newPossibleSuccessorsCount = multipleConnectionsResultNew.possibleSuccessorsCount;
                String newPossiblePredecessorLocalIds = multipleConnectionsResultNew.possiblePredecessorLocalIds;
                String newPossibleSuccessorLocalIds = multipleConnectionsResultNew.possibleSuccessorLocalIds;

                for (MultipleConnectionsResultOld multipleConnectionsResultOld : oldResults) {
                    int oldPostId = multipleConnectionsResultOld.postId;
                    int oldPostHistoryId = multipleConnectionsResultOld.postHistoryId;
                    int oldLocalId = multipleConnectionsResultOld.localId;

                    int oldPostBlockTypeId = multipleConnectionsResultOld.postBlockTypeId;
                    int oldNumberOfPossibleSuccessorsOrPredecessors = multipleConnectionsResultOld.numberOfPossibleSuccessorsOrPredecessors;
                    String oldPossiblePredOrSuccLocalIds = multipleConnectionsResultOld.possiblePredOrSuccLocalIds;

                    if (newPostId == oldPostId
                            && newPostHistoryId == oldPostHistoryId
                            && newLocalId == oldLocalId) {

                        assertEquals(newPostBlockTypeId, oldPostBlockTypeId);

                        if (oldPossiblePredOrSuccLocalIds.equals(newPossiblePredecessorLocalIds)) {
                            assertEquals(oldNumberOfPossibleSuccessorsOrPredecessors, newPossiblePredecessorsCount);
                        } else if (oldPossiblePredOrSuccLocalIds.equals(newPossibleSuccessorLocalIds)) {
                            assertEquals(oldNumberOfPossibleSuccessorsOrPredecessors, newPossibleSuccessorsCount);

                        } else {
                            logger.warning("Entry (" + newPostId + "," + newPostHistoryId + "," + newLocalId
                                    + ") in new file differs from old file with multiple possible connections.");
                        }

                        break;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MultipleConnectionsResultOld {
        int postId;
        int postHistoryId;
        int localId;
        int postBlockTypeId;
        String possiblePredOrSuccLocalIds;
        int numberOfPossibleSuccessorsOrPredecessors;

        MultipleConnectionsResultOld(int postId, int postHistoryId, int localId, int postBlockTypeId,
                                     String possiblePredOrSuccLocalIds,
                                     int numberOfPossibleSuccessorsOrPredecessors) {
            this.postId = postId;
            this.postHistoryId = postHistoryId;
            this.localId = localId;
            this.postBlockTypeId = postBlockTypeId;
            this.possiblePredOrSuccLocalIds = possiblePredOrSuccLocalIds;
            this.numberOfPossibleSuccessorsOrPredecessors = numberOfPossibleSuccessorsOrPredecessors;
        }
    }

    private class MultipleConnectionsResultNew {
        int postId;
        int postHistoryId;
        int localId;
        int postBlockTypeId;
        int possiblePredecessorsCount;
        int possibleSuccessorsCount;
        String possiblePredecessorLocalIds;
        String possibleSuccessorLocalIds;

        MultipleConnectionsResultNew(int postId, int postHistoryId, int localId, int postBlockTypeId,
                                     int possiblePredecessorsCount, int possibleSuccessorsCount,
                                     String possiblePredecessorLocalIds, String possibleSuccessorLocalIds) {
            this.postId = postId;
            this.postHistoryId = postHistoryId;
            this.localId = localId;
            this.postBlockTypeId = postBlockTypeId;
            this.possiblePredecessorsCount = possiblePredecessorsCount;
            this.possibleSuccessorsCount = possibleSuccessorsCount;
            this.possiblePredecessorLocalIds = possiblePredecessorLocalIds;
            this.possibleSuccessorLocalIds = possibleSuccessorLocalIds;
        }
    }

    @Test
    void sampleValidationTest() {
        for (Path samplePath : Statistics.pathsToGTSamples) {
            String sampleName = samplePath.toFile().getName();
            Path pathToPostIdList = Paths.get(samplePath.toString(), sampleName + ".csv");
            Path pathToFiles = Paths.get(samplePath.toString(), "files");
            Path pathToGTs = Paths.get(samplePath.toString(), "completed");

            MetricEvaluationManager manager = MetricEvaluationManager.DEFAULT
                    .withName("TestSampleValidation")
                    .withInputPaths(pathToPostIdList, pathToFiles, pathToGTs)
                    .withValidate(false)
                    .initialize();

            assertTrue(manager.validate());
        }
    }

    @Test
    void testMetricEvaluationManagerWithMultiplePossibleConnections() {
        MetricEvaluationManager manager = MetricEvaluationManager.DEFAULT
                .withName("TestMultiplePossibleConnections")
                .withInputPaths(
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_SO_17-06_sample_100_multiple_possible_links", "PostId_VersionCount_SO_17-06_sample_100_multiple_possible_links.csv"),
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_SO_17-06_sample_100_multiple_possible_links", "files"),
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_SO_17-06_sample_100_multiple_possible_links", "completed"))
                .withOutputDirPath(MetricEvaluationTest.testOutputDir)
                .withDefaultSimilarityMetrics(false)
                .initialize();
        assertEquals(manager.getPostVersionLists().size(), manager.getPostGroundTruths().size());
        assertThat(manager.getPostVersionLists().keySet(), is(manager.getPostGroundTruths().keySet()));

        manager.addSimilarityMetric(
                MetricEvaluationManager.getDefaultSimilarityMetric("equal", 0.3)
        );

        Thread managerThread = new Thread(manager);
        managerThread.start();
        try {
            managerThread.join();
            assertTrue(manager.isFinished()); // assert that execution of manager successfully finished
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testMetricEvaluationManagerWithEqualityMetrics() {
        MetricEvaluationManager manager = MetricEvaluationManager.DEFAULT
                .withName("TestSampleEqual")
                .withInputPaths(
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_17_06_sample_edited_gt", "PostId_VersionCount_17_06_sample_edited_gt.csv"),
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_17_06_sample_edited_gt", "files"),
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_17_06_sample_edited_gt", "completed"))
                .withOutputDirPath(MetricEvaluationTest.testOutputDir)
                .withDefaultSimilarityMetrics(false)
                .initialize();
        assertEquals(manager.getPostVersionLists().size(), manager.getPostGroundTruths().size());
        assertThat(manager.getPostVersionLists().keySet(), is(manager.getPostGroundTruths().keySet()));

        List<Double> similarityThresholds = Arrays.asList(0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9);

        for (double threshold : similarityThresholds) {
            manager.addSimilarityMetric(
                    MetricEvaluationManager.getDefaultSimilarityMetric("equal", threshold)
            );
            manager.addSimilarityMetric(
                    MetricEvaluationManager.getDefaultSimilarityMetric("equalNormalized", threshold)
            );
            manager.addSimilarityMetric(
                    MetricEvaluationManager.getDefaultSimilarityMetric("tokenEqual", threshold)
            );
            manager.addSimilarityMetric(
                    MetricEvaluationManager.getDefaultSimilarityMetric("tokenEqualNormalized", threshold)
            );
        }

        Thread managerThread = new Thread(manager);
        managerThread.start();
        try {
            joinAndValidateEqualMetricResults(manager, managerThread);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testMetricResultsWithEqualMetric() {
        List<MetricEvaluationManager> managers = MetricEvaluationManager.createManagersFromSampleDirectories(
                rootPathToGTSamples, MetricEvaluationTest.testOutputDir, false
        );

        for (MetricEvaluationManager manager : managers) {
            manager.addSimilarityMetric(
                    MetricEvaluationManager.getDefaultSimilarityMetric("equal", 1.0)
            );

            Thread managerThread = new Thread(manager);
            managerThread.start();
            try {
                joinAndValidateEqualMetricResults(manager, managerThread);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void joinAndValidateEqualMetricResults(MetricEvaluationManager manager, Thread managerThread) throws InterruptedException {
        managerThread.join();
        assertTrue(manager.isFinished()); // assert that execution of manager successfully finished
        for (int postId : manager.getPostIds()) {
            // assert that equality-based metric did not produce false positives or failed comparisons
            MetricEvaluationTest.validateEqualMetricResults(manager, postId);
        }
    }

    @Test
    void testMetricEvaluationManagerWithUnclearMatching() {
        MetricEvaluationManager manager = MetricEvaluationManager.DEFAULT
                .withName("TestUnclearMatching")
                .withInputPaths(
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_SO_17_06_sample_unclear_matching", "PostId_VersionCount_SO_17_06_sample_unclear_matching.csv"),
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_SO_17_06_sample_unclear_matching", "files"),
                        Paths.get(rootPathToGTSamples.toString(), "PostId_VersionCount_SO_17_06_sample_unclear_matching", "completed"))
                .withOutputDirPath(MetricEvaluationTest.testOutputDir)
                .withDefaultSimilarityMetrics(false)
                .withNumberOfRepetitions(1)
                .initialize();
        assertEquals(manager.getPostVersionLists().size(), manager.getPostGroundTruths().size());
        assertThat(manager.getPostVersionLists().keySet(), is(manager.getPostGroundTruths().keySet()));

        // lead to "WARNING: (threeShingleJaccard; 0.8): Failure rate must be in range [0.0, 1.0], but was 3.0"
        // when aggregating results
        manager.addSimilarityMetric(new SimilarityMetric(
                "threeShingleJaccard",
                de.unitrier.st.stringsimilarity.set.Variants::threeShingleJaccard,
                SimilarityMetric.MetricType.SET,
                0.8
        ));

        Thread managerThread = new Thread(manager);
        managerThread.start();
        try {
            managerThread.join();
            assertTrue(manager.isFinished()); // assert that execution of manager successfully finished
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
