package com.noajoliver;

import net.sf.saxon.s9api.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SchematronFileValidator class is responsible for validating XML files against a Schematron schema.
 * It provides a GUI for selecting a folder of XML files, running validation, and displaying the results.
 * It also supports pausing and resuming the validation process and handles errors encountered during the process.
 */
public class SchematronFileValidator {

    private static final Logger logger = LoggerFactory.getLogger(SchematronFileValidator.class);
    private static final int THREAD_POOL_SIZE = 8; // Adjust this based on your system's capabilities
    private static final int MAX_LINES_PER_FILE = 100000;

    // Pause and Resume control
    private static final Lock pauseLock = new ReentrantLock();
    private static final Condition pausedCondition = pauseLock.newCondition();
    private static volatile boolean isPaused = false;
    private static final AtomicInteger totalThreads = new AtomicInteger(0);
    private static final AtomicInteger pausedThreads = new AtomicInteger(0);

    private static JLabel activeThreadsLabel;

    /**
     * Main method to launch the application.
     * It initializes the GUI on the Event Dispatch Thread (EDT).
     *
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SchematronFileValidator::createAndShowGUI);
    }

    /**
     * Creates and displays the GUI for the Schematron Validator application.
     * This method initializes all UI components and sets up their layout.
     */
    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Schematron Validator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 700);
        frame.setLayout(new GridBagLayout());

        // UI Components
        JLabel label = new JLabel("Select a folder containing XML files for validation:");
        JButton selectFolderButton = new JButton("Select Folder");
        JLabel fileCountLabel = new JLabel("Files to process: 0");
        JLabel overallStartLabel = new JLabel("Overall Start Time: Not started");
        JLabel overallEndLabel = new JLabel("Overall End Time: Not finished");
        JLabel avgProcessingTimeLabel = new JLabel("Average Processing Time (Multi-threaded): N/A");
        JLabel elapsedTimeLabel = new JLabel("Elapsed Time: 00:00");
        JLabel remainingTimeLabel = new JLabel("Estimated Remaining Time: N/A");
        activeThreadsLabel = new JLabel("Active Threads: 0");

        JButton pauseButton = new JButton("Pause");
        JButton resumeButton = new JButton("Resume");
        resumeButton.setEnabled(false);  // Initially disabled

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JTextArea validationReportArea = new JTextArea();
        validationReportArea.setLineWrap(true);
        validationReportArea.setWrapStyleWord(true);
        validationReportArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(validationReportArea);
        scrollPane.setPreferredSize(new Dimension(580, 150));

        JTextArea fileDurationArea = new JTextArea();
        fileDurationArea.setLineWrap(true);
        fileDurationArea.setWrapStyleWord(true);
        fileDurationArea.setEditable(false);
        JScrollPane fileDurationScrollPane = new JScrollPane(fileDurationArea);
        fileDurationScrollPane.setPreferredSize(new Dimension(580, 150));

        // Layout configuration
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 10, 10, 10);
        frame.add(label, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        selectFolderButton.setPreferredSize(new Dimension(150, 25));
        frame.add(selectFolderButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        frame.add(fileCountLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        frame.add(overallStartLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        frame.add(overallEndLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        frame.add(avgProcessingTimeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        frame.add(elapsedTimeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        frame.add(remainingTimeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        frame.add(activeThreadsLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        frame.add(progressBar, gbc);

        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        frame.add(scrollPane, gbc);

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        frame.add(fileDurationScrollPane, gbc);

        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        frame.add(pauseButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 11;
        gbc.anchor = GridBagConstraints.CENTER;
        frame.add(resumeButton, gbc);

        // Event handling for Select Folder button
        selectFolderButton.addActionListener(e -> {
            JFileChooser folderChooser = new JFileChooser();
            folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int returnValue = folderChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                final String folderPath = folderChooser.getSelectedFile().getAbsolutePath();

                JFileChooser saveFileChooser = new JFileChooser();
                saveFileChooser.setDialogTitle("Save CSV");
                saveFileChooser.setSelectedFile(new File("validation_report.csv"));
                int userSelection = saveFileChooser.showSaveDialog(frame);

                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    final File fileToSave = saveFileChooser.getSelectedFile();
                    final String baseName = fileToSave.getAbsolutePath().replace(".csv", "");
                    final String metricsFileName = baseName + "_metrics.csv";
                    final String countsFileName = baseName + "_counts.csv";
                    final String detailedCountsFileName = baseName + "_detailed_counts.csv";
                    final String errorsFileName = baseName + "_processing_errors.csv";

                    final File folder = new File(folderPath);
                    final File[] xmlFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));

                    if (xmlFiles != null && xmlFiles.length > 0) {
                        fileCountLabel.setText("Files to process: " + xmlFiles.length);
                        progressBar.setMaximum(xmlFiles.length);
                        progressBar.setValue(0);

                        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
                            long overallStartTime;
                            long overallEndTime;

                            @Override
                            protected Void doInBackground() throws Exception {
                                overallStartTime = System.currentTimeMillis();
                                final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                String overallStart = dateFormat.format(new Date(overallStartTime));
                                overallStartLabel.setText("Overall Start Time: " + overallStart);

                                final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
                                final CompletionService<Void> completionService = new ExecutorCompletionService<>(executorService);

                                try (BufferedWriter metricsWriter = new BufferedWriter(new FileWriter(metricsFileName));
                                     BufferedWriter countsWriter = new BufferedWriter(new FileWriter(countsFileName));
                                     BufferedWriter detailedCountsWriter = new BufferedWriter(new FileWriter(detailedCountsFileName));
                                     BufferedWriter errorsWriter = new BufferedWriter(new FileWriter(errorsFileName))) {

                                    metricsWriter.write("file_name,file_size,process_start,process_end,duration_ms\n");
                                    countsWriter.write("file_name,error_count,warning_count\n");
                                    detailedCountsWriter.write("file_name,assertionId,error_count,warning_count\n");
                                    errorsWriter.write("file_name,error_message\n");

                                    final AtomicInteger processedFiles = new AtomicInteger(0);

                                    final AtomicInteger errorFileCounter = new AtomicInteger(1);
                                    final AtomicInteger warningFileCounter = new AtomicInteger(1);

                                    final AtomicInteger errorLines = new AtomicInteger(0);
                                    final AtomicInteger warningLines = new AtomicInteger(0);

                                    final BufferedWriter[] errorWriter = {new BufferedWriter(new FileWriter(baseName + "_errors_1.csv"))};
                                    final BufferedWriter[] warningWriter = {new BufferedWriter(new FileWriter(baseName + "_warnings_1.csv"))};
                                    errorWriter[0].write("file_name,assertionId,description,path,type\n");
                                    warningWriter[0].write("file_name,assertionId,description,path,type\n");

                                    // Submit each XML file for processing
                                    for (final File xmlFile : xmlFiles) {
                                        completionService.submit(() -> {
                                            totalThreads.incrementAndGet();
                                            updateActiveThreadsLabel();
                                            try {
                                                // Handle pause logic
                                                while (true) {
                                                    pauseLock.lock();
                                                    try {
                                                        if (!isPaused) {
                                                            break;
                                                        }
                                                        pausedThreads.incrementAndGet();
                                                        updateActiveThreadsLabel();
                                                        SwingUtilities.invokeLater(() -> {
                                                            validationReportArea.setText("Paused");
                                                            resumeButton.setEnabled(true);
                                                            pauseButton.setEnabled(false);
                                                        });
                                                        pausedCondition.await();
                                                        pausedThreads.decrementAndGet();
                                                        updateActiveThreadsLabel();
                                                    } finally {
                                                        pauseLock.unlock();
                                                    }
                                                }

                                                // Start processing the XML file
                                                final long startTime = System.currentTimeMillis();
                                                final String processStart = dateFormat.format(new Date(startTime));

                                                try {
                                                    final String xmlFilePath = xmlFile.getAbsolutePath();
                                                    final String svrlContent = runValidationAndGetSvrlContent(xmlFilePath);
                                                    final String jsonOutput = parseSvrlContent(svrlContent);

                                                    final Map<String, Integer> errorCounts = new HashMap<>();
                                                    final Map<String, Integer> warningCounts = new HashMap<>();
                                                    int totalErrors = 0;
                                                    int totalWarnings = 0;

                                                    final JSONArray jsonArray = new JSONArray(jsonOutput);
                                                    for (int i = 0; i < jsonArray.length(); i++) {
                                                        final JSONObject jsonObject = jsonArray.getJSONObject(i);
                                                        final String type = jsonObject.optString("type");
                                                        final String assertionId = jsonObject.optString("assertionId");

                                                        if ("error".equals(type)) {
                                                            errorCounts.put(assertionId, errorCounts.getOrDefault(assertionId, 0) + 1);
                                                            totalErrors++;

                                                            synchronized (errorWriter[0]) {
                                                                errorWriter[0].write(formatCsvLine(xmlFile.getName(), jsonObject));
                                                                errorLines.incrementAndGet();
                                                                if (errorLines.get() >= MAX_LINES_PER_FILE) {
                                                                    errorWriter[0].close();
                                                                    errorFileCounter.incrementAndGet();
                                                                    errorWriter[0] = new BufferedWriter(new FileWriter(baseName + "_errors_" + errorFileCounter.get() + ".csv"));
                                                                    errorWriter[0].write("file_name,assertionId,description,path,type\n");
                                                                    errorLines.set(0);
                                                                }
                                                            }
                                                        } else if ("warning".equals(type)) {
                                                            warningCounts.put(assertionId, warningCounts.getOrDefault(assertionId, 0) + 1);
                                                            totalWarnings++;

                                                            synchronized (warningWriter[0]) {
                                                                warningWriter[0].write(formatCsvLine(xmlFile.getName(), jsonObject));
                                                                warningLines.incrementAndGet();
                                                                if (warningLines.get() >= MAX_LINES_PER_FILE) {
                                                                    warningWriter[0].close();
                                                                    warningFileCounter.incrementAndGet();
                                                                    warningWriter[0] = new BufferedWriter(new FileWriter(baseName + "_warnings_" + warningFileCounter.get() + ".csv"));
                                                                    warningWriter[0].write("file_name,assertionId,description,path,type\n");
                                                                    warningLines.set(0);
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Write counts to countsWriter
                                                    synchronized (countsWriter) {
                                                        countsWriter.write(String.format("%s,%d,%d\n", xmlFile.getName(), totalErrors, totalWarnings));
                                                    }

                                                    // Write detailed counts to detailedCountsWriter
                                                    synchronized (detailedCountsWriter) {
                                                        for (final String assertionId : errorCounts.keySet()) {
                                                            detailedCountsWriter.write(String.format("%s,%s,%d,%d\n",
                                                                    xmlFile.getName(), assertionId, errorCounts.get(assertionId), 0));
                                                        }
                                                        for (final String assertionId : warningCounts.keySet()) {
                                                            detailedCountsWriter.write(String.format("%s,%s,%d,%d\n",
                                                                    xmlFile.getName(), assertionId, 0, warningCounts.get(assertionId)));
                                                        }
                                                    }

                                                    final long endTime = System.currentTimeMillis();
                                                    final String processEnd = dateFormat.format(new Date(endTime));
                                                    final long duration = endTime - startTime;

                                                    // Write metrics to metricsWriter
                                                    synchronized (metricsWriter) {
                                                        metricsWriter.write(String.format("%s,%d,%s,%s,%d\n",
                                                                xmlFile.getName(),
                                                                xmlFile.length(),
                                                                processStart,
                                                                processEnd,
                                                                duration));

                                                        publish(String.format(
                                                                "File: %s, Size: %d bytes, Duration: %d ms\n",
                                                                xmlFile.getName(), xmlFile.length(), duration));

                                                        SwingUtilities.invokeLater(() -> {
                                                            int processed = processedFiles.incrementAndGet();
                                                            progressBar.setValue(processed);
                                                            progressBar.setString(String.format("%d / %d", processed, xmlFiles.length));

                                                            long elapsedTime = System.currentTimeMillis() - overallStartTime;
                                                            long estimatedRemainingTime = (elapsedTime / processed) * (xmlFiles.length - processed);

                                                            elapsedTimeLabel.setText("Elapsed Time: " + formatDuration(elapsedTime));
                                                            remainingTimeLabel.setText("Estimated Remaining Time: " + formatDuration(estimatedRemainingTime));
                                                        });
                                                    }
                                                } catch (Exception e) {
                                                    // Handle processing errors
                                                    logger.error("Error processing file: {}", xmlFile.getName(), e);
                                                    synchronized (errorsWriter) {
                                                        errorsWriter.write(String.format("%s,%s\n", xmlFile.getName(), e.getMessage()));
                                                    }
                                                }
                                            } finally {
                                                totalThreads.decrementAndGet();
                                                updateActiveThreadsLabel();
                                            }
                                            return null;
                                        });
                                    }

                                    // Wait for all files to be processed
                                    for (int i = 0; i < xmlFiles.length; i++) {
                                        completionService.take();
                                    }

                                    overallEndTime = System.currentTimeMillis();
                                } finally {
                                    executorService.shutdown();
                                }
                                return null;
                            }

                            @Override
                            protected void process(List<String> chunks) {
                                for (String chunk : chunks) {
                                    fileDurationArea.append(chunk);
                                }
                            }

                            @Override
                            protected void done() {
                                try {
                                    get();
                                    validationReportArea.setText("CSV saved to: " + fileToSave.getAbsolutePath());
                                    fileCountLabel.setText("All files processed");

                                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    String overallEnd = dateFormat.format(new Date(overallEndTime));
                                    overallEndLabel.setText("Overall End Time: " + overallEnd);

                                    long totalTime = overallEndTime - overallStartTime;
                                    long avgTimePerFile = totalTime / xmlFiles.length;
                                    avgProcessingTimeLabel.setText("Average Processing Time (Multi-threaded): " + avgTimePerFile + " ms");

                                    progressBar.setValue(progressBar.getMaximum());
                                    progressBar.setString("Complete");

                                    // Disable both buttons when processing is complete
                                    pauseButton.setEnabled(false);
                                    resumeButton.setEnabled(false);

                                } catch (InterruptedException | ExecutionException ex) {
                                    validationReportArea.setText("An error occurred: " + ex.getMessage());
                                    logger.error("An error occurred during validation and parsing.", ex);
                                }
                            }
                        };

                        // Event handling for Pause button
                        pauseButton.addActionListener(evt -> {
                            pauseLock.lock();
                            try {
                                isPaused = true;
                                SwingUtilities.invokeLater(() -> {
                                    validationReportArea.setText("Pausing...");
                                    pauseButton.setEnabled(false);
                                    resumeButton.setEnabled(true);
                                });
                            } finally {
                                pauseLock.unlock();
                            }
                        });

                        // Event handling for Resume button
                        resumeButton.addActionListener(evt -> {
                            pauseLock.lock();
                            try {
                                isPaused = false;
                                pausedCondition.signalAll();
                                SwingUtilities.invokeLater(() -> {
                                    validationReportArea.setText("Resumed...");
                                    resumeButton.setEnabled(false);
                                    pauseButton.setEnabled(true);
                                });
                            } finally {
                                pauseLock.unlock();
                            }
                        });

                        // Enable pause button when starting the process
                        pauseButton.setEnabled(true);
                        resumeButton.setEnabled(false);

                        worker.execute();
                    } else {
                        validationReportArea.setText("No XML files found in the selected folder.");
                    }
                }
            }
        });

        frame.pack();
        frame.setVisible(true);
    }

    /**
     * Updates the label displaying the count of active threads.
     * This count excludes threads that are currently paused.
     */
    private static void updateActiveThreadsLabel() {
        SwingUtilities.invokeLater(() -> {
            int activeThreads = totalThreads.get() - pausedThreads.get();
            activeThreadsLabel.setText("Active Threads: " + activeThreads);
        });
    }

    /**
     * Runs the validation process on an XML file and returns the generated SVRL content as a string.
     *
     * @param xmlFilePath The path to the XML file to be validated.
     * @return The SVRL content generated by the validation process.
     * @throws IOException        If an I/O error occurs.
     * @throws SaxonApiException  If a Saxon processing error occurs.
     */
    private static String runValidationAndGetSvrlContent(String xmlFilePath) throws IOException, SaxonApiException {
        Processor processor = new Processor(false);
        XsltCompiler compiler = processor.newXsltCompiler();

        XsltExecutable executable;
        try (InputStream finalXsltStream = SchematronFileValidator.class.getClassLoader().getResourceAsStream("final_xslt.xsl")) {

            if (finalXsltStream == null) {
                throw new FileNotFoundException("XSLT file not found in resources.");
            }

            executable = compiler.compile(new StreamSource(finalXsltStream));
        } catch (SaxonApiException e) {
            logger.error("Error compiling XSLT file.", e);
            throw new RuntimeException("Error compiling XSLT file: " + e.getMessage());
        }

        try {
            String xmlContent = new String(Files.readAllBytes(Paths.get(xmlFilePath)));

            XdmNode source = processor.newDocumentBuilder().build(new StreamSource(new ByteArrayInputStream(xmlContent.getBytes())));
            XsltTransformer transformer = executable.load();
            transformer.setInitialContextNode(source);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Serializer serializer = processor.newSerializer(outputStream);
            serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            transformer.setDestination(serializer);
            transformer.transform();

            return outputStream.toString();

        } catch (IOException | SaxonApiException e) {
            logger.error("Error processing XML file.", e);
            throw e;
        }
    }

    /**
     * Parses the SVRL content and returns the results as a JSON string.
     *
     * @param svrlContent The SVRL content to be parsed.
     * @return The parsed results as a JSON string.
     */
    private static String parseSvrlContent(String svrlContent) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new ByteArrayInputStream(svrlContent.getBytes()));
            doc.getDocumentElement().normalize();

            List<FailedAssertion> failedAssertions = parseFailedAssertions(doc);

            return createJsonFromFailedAssertions(failedAssertions);

        } catch (Exception e) {
            logger.error("Error parsing SVRL content.", e);
            return "Error parsing SVRL content: " + e.getMessage();
        }
    }

    /**
     * Parses the failed assertions from the SVRL document and returns them as a list of FailedAssertion objects.
     *
     * @param doc The SVRL document to parse.
     * @return A list of FailedAssertion objects representing the failed assertions in the SVRL document.
     */
    private static List<FailedAssertion> parseFailedAssertions(Document doc) {
        List<FailedAssertion> failedAssertions = new ArrayList<>();
        NodeList failedAssertList = doc.getElementsByTagName("svrl:failed-assert");

        for (int i = 0; i < failedAssertList.getLength(); i++) {
            Node node = failedAssertList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String id = element.getAttribute("id");
                String test = element.getAttribute("test");
                String location = element.getAttribute("location");
                String text = element.getElementsByTagName("svrl:text").item(0).getTextContent();

                String type = getFiredRuleIdForAssertion(element);

                if (id.isEmpty()) {
                    id = extractIdFromText(text);
                }

                failedAssertions.add(new FailedAssertion(id, test, location, text, type));
            }
        }
        return failedAssertions;
    }

    /**
     * Retrieves the rule ID that triggered the failed assertion and determines if it is an error or warning.
     *
     * @param assertionElement The assertion element to analyze.
     * @return A string representing the type of the assertion ("error" or "warning").
     */
    private static String getFiredRuleIdForAssertion(Element assertionElement) {
        Node nextNode = assertionElement.getNextSibling();
        while (nextNode != null) {
            if (nextNode.getNodeType() == Node.ELEMENT_NODE &&
                    ((Element) nextNode).getTagName().equals("svrl:fired-rule")) {
                String ruleId = ((Element) nextNode).getAttribute("id");
                if (ruleId.contains("warnings")) {
                    return "warning";
                } else {
                    return "error";
                }
            }
            nextNode = nextNode.getNextSibling();
        }
        return "error";
    }

    /**
     * Extracts the ID from the assertion text using a regular expression pattern.
     *
     * @param text The assertion text to extract the ID from.
     * @return The extracted ID as a string.
     */
    private static String extractIdFromText(String text) {
        String id = "";
        Pattern pattern = Pattern.compile("CONF:([0-9-]+(?: through [0-9-]+)?)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            id = matcher.group(1);
        }
        return id;
    }

    /**
     * Converts a list of FailedAssertion objects into a JSON string representation.
     *
     * @param failedAssertions The list of FailedAssertion objects to convert.
     * @return A JSON string representing the failed assertions.
     */
    private static String createJsonFromFailedAssertions(List<FailedAssertion> failedAssertions) {
        JSONArray errorArray = new JSONArray();
        for (FailedAssertion fa : failedAssertions) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("assertionId", fa.id);
            jsonObject.put("description", fa.text);
            jsonObject.put("path", fa.location);
            jsonObject.put("type", fa.type);
            errorArray.put(jsonObject);
        }
        return errorArray.toString();
    }

    /**
     * Formats a JSON object representing a failed assertion into a CSV line.
     *
     * @param fileName   The name of the XML file being processed.
     * @param jsonObject The JSON object representing the failed assertion.
     * @return A formatted CSV line as a string.
     */
    private static String formatCsvLine(String fileName, JSONObject jsonObject) {
        String cleanedDescription = jsonObject.optString("description")
                .replaceAll("\\r?\\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        int maxLength = 300;
        if (cleanedDescription.length() > maxLength) {
            cleanedDescription = cleanedDescription.substring(0, maxLength) + "...";
        }

        cleanedDescription = "\"" + cleanedDescription.replace("\"", "\"\"") + "\"";

        return String.format("%s,%s,%s,%s,%s\n",
                fileName,
                jsonObject.optString("assertionId"),
                cleanedDescription,
                jsonObject.optString("path"),
                jsonObject.optString("type")
        );
    }

    /**
     * Formats a duration in milliseconds into a human-readable string.
     *
     * @param durationMillis The duration in milliseconds.
     * @return A formatted string representing the duration in a human-readable format.
     */
    private static String formatDuration(long durationMillis) {
        long seconds = durationMillis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        if (days > 0) {
            return String.format("%d days, %02d:%02d:%02d", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}

/**
 * The FailedAssertion class represents a single failed assertion from the Schematron validation.
 * It includes information such as the ID, test expression, location in the document, text of the assertion, and type (error or warning).
 */
class FailedAssertion {
    String id;
    String test;
    String location;
    String text;
    String type;

    /**
     * Constructor to create a new FailedAssertion object.
     *
     * @param id       The ID of the assertion.
     * @param test     The test expression that failed.
     * @param location The location in the document where the assertion failed.
     * @param text     The text of the failed assertion.
     * @param type     The type of the assertion (error or warning).
     */
    FailedAssertion(String id, String test, String location, String text, String type) {
        this.id = id;
        this.test = test;
        this.location = location;
        this.text = text;
        this.type = type;
    }

    @Override
    public String toString() {
        return "ID: " + id + ", Test: " + test + ", Location: " + location + ", Text: " + text + ", Type: " + type;
    }
}
