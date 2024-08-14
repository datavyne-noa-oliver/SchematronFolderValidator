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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SchematronFileValidator {

    private static final Logger logger = LoggerFactory.getLogger(SchematronFileValidator.class);
    private static final int THREAD_POOL_SIZE = 8; // Adjust this based on your system's capabilities

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SchematronFileValidator::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Schematron Validator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 650);  // Increased height to accommodate progress bar
        frame.setLayout(new GridBagLayout());

        JLabel label = new JLabel("Select a folder containing XML files for validation:");
        JButton selectFolderButton = new JButton("Select Folder");
        JLabel fileCountLabel = new JLabel("Files to process: 0");
        JLabel overallStartLabel = new JLabel("Overall Start Time: Not started");
        JLabel overallEndLabel = new JLabel("Overall End Time: Not finished");
        JLabel avgProcessingTimeLabel = new JLabel("Average Processing Time: N/A");

        // Add progress bar
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

        // Set layout constraints
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

        // Add progress bar to the layout
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        frame.add(progressBar, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        frame.add(scrollPane, gbc);

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        frame.add(fileDurationScrollPane, gbc);

        selectFolderButton.addActionListener(e -> {
            JFileChooser folderChooser = new JFileChooser();
            folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int returnValue = folderChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                String folderPath = folderChooser.getSelectedFile().getAbsolutePath();

                // Open another GUI window to select the save location
                JFileChooser saveFileChooser = new JFileChooser();
                saveFileChooser.setDialogTitle("Save CSV");
                saveFileChooser.setSelectedFile(new File("validation_report.csv"));
                int userSelection = saveFileChooser.showSaveDialog(frame);

                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    File fileToSave = saveFileChooser.getSelectedFile();
                    String metricsFileName = fileToSave.getAbsolutePath().replace(".csv", "_metrics.csv");

                    File folder = new File(folderPath);
                    File[] xmlFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));

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
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                String overallStart = dateFormat.format(new Date(overallStartTime));
                                overallStartLabel.setText("Overall Start Time: " + overallStart);

                                ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
                                CompletionService<Void> completionService = new ExecutorCompletionService<>(executorService);

                                try (BufferedWriter csvWriter = new BufferedWriter(new FileWriter(fileToSave));
                                     BufferedWriter metricsWriter = new BufferedWriter(new FileWriter(metricsFileName))) {

                                    csvWriter.write("file_name,assertionId,description,path,type\n");
                                    metricsWriter.write("file_name,file_size,process_start,process_end,duration_ms\n");

                                    AtomicInteger processedFiles = new AtomicInteger(0);

                                    for (File xmlFile : xmlFiles) {
                                        completionService.submit(() -> {
                                            long startTime = System.currentTimeMillis();
                                            String processStart = dateFormat.format(new Date(startTime));

                                            String xmlFilePath = xmlFile.getAbsolutePath();
                                            Path svrlFilePath = runValidationAndSaveReport(xmlFilePath);
                                            String jsonOutput = parseSvrlFile(svrlFilePath.toString());

                                            long endTime = System.currentTimeMillis();
                                            String processEnd = dateFormat.format(new Date(endTime));
                                            long duration = endTime - startTime;

                                            synchronized (csvWriter) {
                                                // Append results to CSV file
                                                appendJsonToCsv(jsonOutput, csvWriter, xmlFile.getName());

                                                // Save metrics to metrics CSV
                                                metricsWriter.write(String.format("%s,%d,%s,%s,%d\n",
                                                        xmlFile.getName(),
                                                        xmlFile.length(),
                                                        processStart,
                                                        processEnd,
                                                        duration));

                                                // Publish file processing details
                                                publish(String.format(
                                                        "File: %s, Size: %d bytes, Duration: %d ms\n",
                                                        xmlFile.getName(), xmlFile.length(), duration));

                                                // Update progress
                                                SwingUtilities.invokeLater(() -> {
                                                    int processed = processedFiles.incrementAndGet();
                                                    progressBar.setValue(processed);
                                                    progressBar.setString(String.format("%d / %d", processed, xmlFiles.length));
                                                });
                                            }
                                            return null;
                                        });
                                    }

                                    for (int i = 0; i < xmlFiles.length; i++) {
                                        completionService.take(); // Wait for each file to finish processing
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
                                    get(); // Retrieve any exceptions thrown during doInBackground
                                    validationReportArea.setText("CSV saved to: " + fileToSave.getAbsolutePath());
                                    fileCountLabel.setText("All files processed");

                                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    String overallEnd = dateFormat.format(new Date(overallEndTime));
                                    overallEndLabel.setText("Overall End Time: " + overallEnd);

                                    long totalTime = overallEndTime - overallStartTime;
                                    long avgTimePerFile = totalTime / xmlFiles.length;
                                    avgProcessingTimeLabel.setText("Average Processing Time: " + avgTimePerFile + " ms");

                                    progressBar.setValue(progressBar.getMaximum());
                                    progressBar.setString("Complete");

                                } catch (InterruptedException | ExecutionException ex) {
                                    validationReportArea.setText("An error occurred: " + ex.getMessage());
                                    logger.error("An error occurred during validation and parsing.", ex);
                                }
                            }
                        };

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

    private static Path runValidationAndSaveReport(String xmlFilePath) throws IOException, SaxonApiException {
        Processor processor = new Processor(false);
        XsltCompiler compiler = processor.newXsltCompiler();

        XsltExecutable executable;
        try {
            // Load the XSLT file from the classpath
            InputStream finalXsltStream = SchematronFileValidator.class.getClassLoader().getResourceAsStream("final_xslt.xsl");

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

            XdmDestination intermediateDestination = new XdmDestination();
            transformer.setDestination(intermediateDestination);
            transformer.transform();

            // Save the intermediate report to a temporary file
            Path tempFilePath = Files.createTempFile("svrl_output", ".xml");
            saveXdmNodeToFile(processor, intermediateDestination.getXdmNode(), tempFilePath);

            logger.info("SVRL file created at: {}", tempFilePath.toAbsolutePath());
            return tempFilePath;

        } catch (IOException | SaxonApiException e) {
            logger.error("Error processing XML file.", e);
            throw e;
        }
    }

    private static void saveXdmNodeToFile(Processor processor, XdmNode node, Path filePath) {
        try {
            Serializer serializer = processor.newSerializer(filePath.toFile());
            serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
            serializer.serializeNode(node);
            logger.info("SVRL file saved at: {}", filePath.toAbsolutePath());
        } catch (SaxonApiException e) {
            logger.error("Error saving SVRL file.", e);
        }
    }

    private static String parseSvrlFile(String svrlFilePath) {
        try {
            File inputFile = new File(svrlFilePath);
            if (!inputFile.exists()) {
                throw new FileNotFoundException("The SVRL file does not exist at the provided path: " + svrlFilePath);
            }

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            List<FailedAssertion> failedAssertions = parseFailedAssertions(doc);

            // Create and return the JSON output
            return createJsonFromFailedAssertions(failedAssertions);

        } catch (Exception e) {
            logger.error("Error parsing SVRL file.", e);
            return "Error parsing SVRL file: " + e.getMessage();
        }
    }

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

                // Use the updated method to determine the assertion type
                String type = getFiredRuleIdForAssertion(element);

                // If id is not present, extract from text
                if (id.isEmpty()) {
                    id = extractIdFromText(text);
                }

                failedAssertions.add(new FailedAssertion(id, test, location, text, type));
            }
        }
        return failedAssertions;
    }

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
        // Default to "error" if no fired-rule is found
        return "error";
    }

    private static String extractIdFromText(String text) {
        String id = "";
        Pattern pattern = Pattern.compile("CONF:([0-9-]+(?: through [0-9-]+)?)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            id = matcher.group(1);
        }
        return id;
    }

    private static String createJsonFromFailedAssertions(List<FailedAssertion> failedAssertions) {
        JSONArray errorArray = new JSONArray();
        for (FailedAssertion fa : failedAssertions) {
            if ("error".equals(fa.type)) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("assertionId", fa.id);
                jsonObject.put("description", fa.text);
                jsonObject.put("path", fa.location);
                jsonObject.put("type", fa.type);
                errorArray.put(jsonObject);
            }
        }
        return errorArray.toString();
    }

    private static void appendJsonToCsv(String jsonOutput, BufferedWriter csvWriter, String fileName) throws IOException {
        JSONArray jsonArray = new JSONArray(jsonOutput);
        int maxLength = 300;  // Set the maximum length for the description

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);

            // Clean up the description by removing line breaks and trimming spaces
            String cleanedDescription = jsonObject.optString("description")
                    .replaceAll("\\r?\\n", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            // Truncate the description if it exceeds the maximum length
            if (cleanedDescription.length() > maxLength) {
                cleanedDescription = cleanedDescription.substring(0, maxLength) + "...";
            }

            // Enclose the description in quotes to handle commas and special characters
            cleanedDescription = "\"" + cleanedDescription.replace("\"", "\"\"") + "\"";

            csvWriter.write(String.format("%s,%s,%s,%s,%s\n",
                    fileName,
                    jsonObject.optString("assertionId"),
                    cleanedDescription,
                    jsonObject.optString("path"),
                    jsonObject.optString("type")
            ));
        }
    }
}

class FailedAssertion {
    String id;
    String test;
    String location;
    String text;
    String type;

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
