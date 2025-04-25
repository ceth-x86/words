# English Words Study App

A Clojure application for studying English words with SQLite integration.

## Features

- SQLite database integration
- Store English words with transcription, description, translation, and examples
- Command-line interface
- REST API for all functionality
- Search functionality
- Word management (add, update, delete)
- No duplicate words (words are unique)
- Import words from formatted text files
- Export words to formatted text files

## Database Schema

The application creates a `words` table with the following structure:

```sql
CREATE TABLE IF NOT EXISTS words (
  word TEXT PRIMARY KEY NOT NULL,
  transcription TEXT,
  description TEXT,
  translation TEXT NOT NULL,
  examples TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

## Usage

### Command Line Interface

The application provides a command-line interface with the following commands:

```bash
# Show usage information
lein run

# Initialize the database
lein run init

# Add a new word (all parameters must be in quotes)
lein run add "word" "transcription" "description" "translation" "examples"

# Show all words in the database
lein run show

# Get a specific word
lein run get "word"

# Update an existing word
lein run update "word" "new_transcription" "new_description" "new_translation" "new_examples"

# Delete a word
lein run delete "word"

# Search for words
lein run search "term"

# Import words from a file
lein run import "path/to/file.txt"

# Export all words to a file
lein run export "path/to/file.txt"

# Export search results to a file
lein run export-search "search_term" "path/to/file.txt"

# Start the REST API server (optional port, default 3000)
lein run server [port]
```

### Import Format

The application can import words from a text file with the following format:

```
**word** /transcription/ - description 
(translation)
- Example 1.
- Example 2.

**next_word** /transcription/ - description 
(translation)
- Example 1.
```

Words are separated by blank lines. Each word entry follows this structure:
1. First line: Word in `**double asterisks**`, followed by transcription in `/slashes/`, followed by description after a dash
2. Second line: Translation in (parentheses)
3. Subsequent lines: Examples, each starting with a dash (-) 

### Examples

```bash
# Initialize the database
lein run init

# Add a new word
lein run add "apple" "ˈæp(ə)l" "A round fruit with red, green, or yellow skin and crisp flesh" "яблоко" "I eat an apple every day. She likes green apples."

# Get information about a specific word
lein run get "apple"

# Update a word
lein run update "apple" "ˈæp(ə)l" "A round fruit with firm, juicy flesh and red, yellow, or green skin" "яблоко" "He gave me an apple. The apple tree is blooming."

# Delete a word
lein run delete "apple"

# Search for words containing "app"
lein run search "app"

# Import words from a file
lein run import "resources/sample_words.txt"

# Export all words to a file
lein run export "resources/exported_words.txt"

# Export search results to a file
lein run export-search "app" "resources/app_words.txt"
```

### Default behavior

If you run the application without arguments, it will display usage information.

```bash
lein run
```

## Database Functions

The application provides several functions for database operations:

- `db/init-db!` - Initialize the database and create the table
- `db/insert-word!` - Insert a new word with all its details
- `db/get-all-words` - Get all words from the database
- `db/get-word-by-word` - Get a specific word by its word string
- `db/update-word!` - Update an existing word
- `db/delete-word!` - Delete a word
- `db/search-words` - Search for words by English word or translation
- `db/batch-import-words!` - Import multiple words at once

## Export Functions

The application provides several functions for exporting words:

- `exp/format-word-entry` - Format a single word entry for export
- `exp/format-words-for-export` - Format multiple words for export
- `exp/export-words-to-file` - Export all words to a file
- `exp/export-search-results-to-file` - Export search results to a file

## REST API

The application also provides a REST API with the following endpoints:

### Basic Endpoints

- `GET /api/words` - Get all words
- `GET /api/words/:word` - Get a specific word
- `GET /api/search?term=...` - Search for words
- `POST /api/words` - Add a new word
- `PUT /api/words/:word` - Update a word
- `DELETE /api/words/:word` - Delete a word

### Database Management

- `POST /api/init` - Initialize the database

### Import/Export Endpoints

- `POST /api/import` - Import words from file upload
- `GET /api/export` - Export all words
- `GET /api/export/search?term=...` - Export search results

### Examples

#### Get all words
```
GET /api/words
```

#### Get a specific word
```
GET /api/words/apple
```

#### Search for words
```
GET /api/search?term=app
```

#### Add a new word
```
POST /api/words
Content-Type: application/json

{
  "word": "apple",
  "transcription": "ˈæp(ə)l",
  "description": "A round fruit with red, green, or yellow skin and crisp flesh",
  "translation": "яблоко",
  "examples": "I eat an apple every day.\nShe likes green apples."
}
```

#### Update a word
```
PUT /api/words/apple
Content-Type: application/json

{
  "transcription": "ˈæp(ə)l",
  "description": "A round fruit with firm, juicy flesh and red, yellow, or green skin",
  "translation": "яблоко",
  "examples": "He gave me an apple.\nThe apple tree is blooming."
}
```

#### Delete a word
```
DELETE /api/words/apple
```

#### Import words from a file
```
POST /api/import
Content-Type: multipart/form-data; boundary=----FormBoundary

------FormBoundary
Content-Disposition: form-data; name="file"; filename="words.txt"
Content-Type: text/plain

... file content ...
------FormBoundary--
```

#### Export all words
```
GET /api/export
```

#### Export search results
```
GET /api/export/search?term=app
```

### Curl Examples

Here are some curl command examples to interact with the API:

#### Initialize the database
```bash
curl -X POST http://localhost:3000/api/init
```

#### Get all words
```bash
curl http://localhost:3000/api/words
```

#### Get a specific word
```bash
curl http://localhost:3000/api/words/apple
```

#### Search for words
```bash
curl "http://localhost:3000/api/search?term=app"
```

#### Add a new word
```bash
curl -X POST http://localhost:3000/api/words \
     -H "Content-Type: application/json" \
     -d '{
           "word": "apple",
           "transcription": "ˈæp(ə)l",
           "description": "A round fruit with red, green, or yellow skin and crisp flesh",
           "translation": "яблоко",
           "examples": "I eat an apple every day.\nShe likes green apples."
         }'
```

#### Update a word
```bash
curl -X PUT http://localhost:3000/api/words/apple \
     -H "Content-Type: application/json" \
     -d '{
           "transcription": "ˈæp(ə)l",
           "description": "A round fruit with firm, juicy flesh and red, yellow, or green skin",
           "translation": "яблоко",
           "examples": "He gave me an apple.\nThe apple tree is blooming."
         }'
```

#### Delete a word
```bash
curl -X DELETE http://localhost:3000/api/words/apple
```

#### Import words from a file
```bash
curl -X POST http://localhost:3000/api/import \
     -F "file=@/path/to/words.txt"
```

#### Export all words
```bash
curl -o exported_words.txt http://localhost:3000/api/export
```

#### Export search results
```bash
curl -o search_results.txt "http://localhost:3000/api/export/search?term=app"
```

## Testing

The application includes comprehensive test coverage for all components:

### Running Tests

Run all tests with:

```bash
lein test
```

Run tests for a specific namespace:

```bash
lein test my-app.core-test
lein test my-app.db-test
lein test my-app.db-schema-test
lein test my-app.api-test
```

### Test Coverage

The test suite includes:

- **Core Tests**: Tests for the CLI interface and functions
- **Database Tests**: Tests for database CRUD operations, search functionality, and batch imports
- **Schema Tests**: Tests for database schema structure, constraints, and data integrity
- **API Tests**: Tests for REST API endpoints, request validation, and response formatting

### Database Testing Strategy

The database tests use a file-based SQLite database in a temporary location, which is:
- Created before each test
- Initialized with the appropriate schema
- Populated with test data
- Used for testing operations
- Deleted after tests complete

This approach isolates the tests from the main application database and ensures each test run starts with a clean database state.

### Schema Testing

The schema tests specifically verify:
- Correct database structure and column types
- Primary key constraint enforcement
- NOT NULL constraints
- Default values (timestamps)
- Index creation

### API Testing Strategy

The API tests use Ring Mock to simulate HTTP requests and verify:
- Correct response status codes (200, 404, 500)
- Proper JSON response formatting
- Response headers (Content-Type, Content-Disposition)
- Request parameter validation
- Error handling
- Content types and download headers for export functionality

### Testing Approach

- Core functionality is tested with mocks for database interactions
- Database functionality is tested with a real SQLite database in a temporary location
- API endpoints are tested with simulated HTTP requests using ring-mock
- Each test is isolated and doesn't affect other tests

## License

Copyright © 2023 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
