# Developer Quickstart

## Prerequisites

- **Java 21** or later
- **Maven 3.8+**

## Common Commands

### Build

```bash
# Clean and build
mvn clean package

# Fast compile (no tests)
mvn clean compile

# Skip tests during build
mvn clean package -DskipTests
```

### Run

```bash
# Run application
mvn javafx:run

# Run with debug logs
mvn -e javafx:run
```

### Test

```bash
# Run all tests
mvn test

# Run tests with coverage report
mvn test jacoco:report

# Run specific test class
mvn test -Dtest=PatentDaoTest
```

### IDE Setup

**VS Code:**
- Install Java Extension Pack
- Debug configuration: Add to `.vscode/launch.json`:
```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug (Launch)",
      "request": "launch",
      "mainClass": "com.patenttracker/com.patenttracker.App",
      "projectName": "patent-tracker"
    }
  ]
}
```

**IntelliJ IDEA:**
- Import as Maven project
- Ensure Java 21 SDK is selected
- Enable automatic importing of Maven changes

### Build a Standalone JAR

```bash
mvn clean package -Pfat-jar
java -jar target/patent-tracker-1.0.0.jar
```

## Development Tips

- Use `mvn -e` for detailed error messages
- Use `mvn -X` for debug output
- Test changes with `mvn test` before committing
- Check coverage with `mvn test jacoco:report` then view `target/site/jacoco/index.html`
