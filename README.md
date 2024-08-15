Schematron Validator
Overview
The Schematron Validator is a Java-based application that validates XML files against a Schematron schema. This application provides a graphical user interface (GUI) for selecting a folder containing XML files, running validation processes, and viewing validation results. The application supports multi-threaded validation and offers functionality to pause and resume the process. It also includes error handling mechanisms to capture and log any issues that occur during validation.

Features
Multi-threaded Validation: Efficiently processes multiple XML files simultaneously using configurable thread pools.
Pause/Resume Functionality: Users can pause and resume the validation process at any time.
Detailed Metrics: The application generates detailed CSV reports, including metrics, error counts, and detailed validation results.
Error Handling: Captures processing errors and logs them in a dedicated CSV file.
User-Friendly Interface: A simple and intuitive GUI that allows users to select folders, monitor progress, and view results.
Installation
Clone the Repository:

bash
Copy code
git clone <repository-url>
Build the Project:
Use a build tool like Gradle or Maven to compile the project. Ensure you have the necessary dependencies, particularly for the Saxon library.

Run the Application:

bash
Copy code
java -jar SchematronValidator.jar
Usage
Running the Validator
Select Folder: Click the "Select Folder" button to choose the directory containing the XML files you want to validate.

Choose Output Location: After selecting the folder, you will be prompted to choose a location to save the CSV reports.

Start Validation: The application will automatically begin validating the XML files. The progress is displayed in the progress bar, and the number of active threads is shown in real-time.

Pause/Resume: Use the "Pause" button to temporarily stop the validation process. The "Resume" button will become active, allowing you to continue the process.

View Results: Once the validation is complete, you can view the results in the specified CSV files.

CSV Output Files
_metrics.csv: Contains metrics for each XML file, including file size, processing start and end times, and duration.
_counts.csv: Summarizes the total errors and warnings found in each file.
_detailed_counts.csv: Provides a detailed breakdown of errors and warnings, including assertion IDs and locations.
_processing_errors.csv: Logs any errors that occurred during the processing of XML files, including the file name and error message.
Configuration
THREAD_POOL_SIZE: Adjust the number of threads used for processing by modifying the THREAD_POOL_SIZE constant in the SchematronFileValidator class. The default is set to 8 threads.

MAX_LINES_PER_FILE: Configure the maximum number of lines per error/warning file by adjusting the MAX_LINES_PER_FILE constant.

Code Structure
SchematronFileValidator.java: The main class that handles the GUI, validation logic, threading, and file operations.
FailedAssertion.java: A helper class representing a failed assertion within the validation process.
Dependencies
Saxon HE: For XSLT processing and Schematron validation.
SLF4J: For logging purposes.
JSON: For handling and generating JSON outputs.
Error Handling
If an XML file contains non-UTF-8 characters or any other issues that prevent processing, the application will record the error in the _processing_errors.csv file and continue processing the remaining files.

Contribution
Feel free to fork this repository and submit pull requests. Any contributions to improve the functionality or usability of this tool are welcome.

License
This project is licensed under the MIT License. See the LICENSE file for more details.