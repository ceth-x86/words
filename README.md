# English Words Study App

A Clojure application for studying English words with SQLite integration.

## Features

- SQLite database integration
- Store English words with multiple meanings, transcriptions, descriptions, translations, and examples
- Command-line interface
- REST API for all functionality
- Search functionality
- Word and meaning management (add, update, delete)
- No duplicate words (words are unique)
- Import words from formatted text files
- Export words to formatted text files
- Support for multiple meanings per word

## Database Schema

The application creates the following tables:

```sql
-- Words table for storing unique words
CREATE TABLE IF NOT EXISTS words (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  word TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(word)
)

-- Meanings table for storing different meanings of words
CREATE TABLE IF NOT EXISTS meanings (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  word_id INTEGER NOT NULL,
  transcription TEXT,
  description TEXT,
  translation TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
)

-- Examples table for storing examples for each meaning
CREATE TABLE IF NOT EXISTS examples (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  meaning_id INTEGER NOT NULL,
  text TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (meaning_id) REFERENCES meanings(id) ON DELETE CASCADE
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

# Add a new word or meaning (all parameters must be in quotes)
lein run add "word" "transcription" "description" "translation" "examples"

# Add a new meaning to an existing word
lein run add-meaning "word" "transcription" "description" "translation" "examples"

# Show all words in the database
lein run show

# Get a specific word with all its meanings
lein run get "word"

# Update a specific meaning
lein run update-meaning "meaning_id" "new_transcription" "new_description" "new_translation"

# Delete a specific meaning
lein run delete-meaning "meaning_id"

# Delete a word and all its meanings
lein run delete "word"

# Add an example to a meaning
lein run add-example "meaning_id" "example_text"

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

----------

/transcription/ - alternate description
(alternate translation)
- Example 3.
- Example 4.

**next_word** /transcription/ - description 
(translation)
- Example 1.
```

Words are separated by blank lines. Each word entry follows this structure:
1. First line: Word in `**double asterisks**`, followed by transcription in `/slashes/`, followed by description after a dash
2. Second line: Translation in (parentheses)
3. Subsequent lines: Examples, each starting with a dash (-) 
4. Different meanings of the same word are separated by a delimiter line (---------- or ==========)
5. For subsequent meanings, you only need to include the transcription, description, and translation (the word is implied)

### Examples

```bash
# Initialize the database
lein run init

# Add a new word with its first meaning
lein run add "run" "rʌn" "to move quickly on foot" "бежать" "I run every morning.\nShe runs faster than me."

# Add another meaning to the existing word
lein run add-meaning "run" "rʌn" "to operate or function" "работать" "The machine runs smoothly.\nThis program is running well."

# Get information about a specific word (shows all meanings)
lein run get "run"

# Update a specific meaning (requires the meaning ID)
lein run update-meaning "2" "rʌn" "to operate or manage something" "управлять" 

# Add an example to a meaning
lein run add-example "2" "They run a successful business."

# Delete a specific meaning
lein run delete-meaning "2"

# Delete a word and all its meanings
lein run delete "run"

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

- `db/init-db!` - Initialize the database and create the tables
- `db/insert-word!` - Insert a new word with a meaning and examples
- `db/add-meaning-to-word!` - Add a new meaning to an existing word
- `db/get-all-words` - Get all words with their meanings and examples
- `db/get-word-by-word` - Get a specific word with all its meanings and examples
- `db/update-meaning!` - Update a specific meaning
- `db/delete-meaning!` - Delete a specific meaning
- `db/delete-word!` - Delete a word and all its meanings
- `db/add-example-to-meaning!` - Add an example to a meaning
- `db/delete-example!` - Delete a specific example
- `db/search-words` - Search for words by English word or translation
- `db/count-words` - Count the total number of words
- `db/count-meanings` - Count the total number of meanings
- `db/count-examples` - Count the total number of examples

## Export Functions

The application provides several functions for exporting words:

- `exp/format-examples` - Format examples for export
- `exp/format-meaning` - Format a single meaning for export
- `exp/format-word-entry` - Format a word entry with all its meanings for export
- `exp/format-words-for-export` - Format multiple words for export
- `exp/export-words-to-file` - Export all words to a file
- `exp/export-search-results-to-file` - Export search results to a file

## REST API

The application also provides a REST API with the following endpoints:

### Word Endpoints

- `GET /api/words` - Get all words with meanings and examples
- `GET /api/words/:word` - Get a specific word with meanings and examples
- `GET /api/search?term=...` - Search for words
- `POST /api/words` - Add a new word with a meaning

### Meaning Endpoints

- `POST /api/words/:word/meanings` - Add a new meaning to a word
- `PUT /api/meanings/:id` - Update a specific meaning
- `DELETE /api/meanings/:id` - Delete a specific meaning

### Example Endpoints

- `POST /api/meanings/:id/examples` - Add an example to a meaning
- `DELETE /api/examples/:id` - Delete a specific example

### Word Management

- `DELETE /api/words/:word` - Delete a word and all its meanings

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
GET /api/words/run
```

#### Search for words
```
GET /api/search?term=run
```

#### Add a new word with a meaning
```
POST /api/words
Content-Type: application/json

{
  "word": "run",
  "transcription": "rʌn",
  "description": "to move quickly on foot",
  "translation": "бежать",
  "examples": "I run every morning.\nShe runs faster than me."
}
```

#### Add a new meaning to a word
```
POST /api/words/run/meanings
Content-Type: application/json

{
  "transcription": "rʌn",
  "description": "to operate or function",
  "translation": "работать",
  "examples": "The machine runs smoothly.\nThis program is running well."
}
```

#### Update a meaning
```
PUT /api/meanings/2
Content-Type: application/json

{
  "transcription": "rʌn",
  "description": "to operate or manage something",
  "translation": "управлять"
}
```

#### Add an example to a meaning
```
POST /api/meanings/2/examples
Content-Type: application/json

{
  "text": "They run a successful business."
}
```

#### Delete a meaning
```
DELETE /api/meanings/2
```

#### Delete an example
```
DELETE /api/examples/3
```

#### Delete a word and all its meanings
```
DELETE /api/words/run
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
GET /api/export/search?term=run
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

#### Get a specific word with all its meanings
```bash
curl http://localhost:3000/api/words/run
```

#### Search for words
```bash
curl "http://localhost:3000/api/search?term=run"
```

#### Add a new word with a meaning
```bash
curl -X POST http://localhost:3000/api/words \
     -H "Content-Type: application/json" \
     -d '{
           "word": "run",
           "transcription": "rʌn",
           "description": "to move quickly on foot",
           "translation": "бежать",
           "examples": "I run every morning.\nShe runs faster than me."
         }'
```

#### Add a new meaning to a word
```bash
curl -X POST http://localhost:3000/api/words/run/meanings \
     -H "Content-Type: application/json" \
     -d '{
           "transcription": "rʌn",
           "description": "to operate or function",
           "translation": "работать",
           "examples": "The machine runs smoothly.\nThis program is running well."
         }'
```

#### Update a meaning
```bash
curl -X PUT http://localhost:3000/api/meanings/2 \
     -H "Content-Type: application/json" \
     -d '{
           "transcription": "rʌn",
           "description": "to operate or manage something",
           "translation": "управлять"
         }'
```

#### Add an example to a meaning
```bash
curl -X POST http://localhost:3000/api/meanings/2/examples \
     -H "Content-Type: application/json" \
     -d '{
           "text": "They run a successful business."
         }'
```

#### Delete a meaning
```bash
curl -X DELETE http://localhost:3000/api/meanings/2
```

#### Delete a word and all its meanings
```bash
curl -X DELETE http://localhost:3000/api/words/run
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
curl -o search_results.txt "http://localhost:3000/api/export/search?term=run"
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
lein test my-app.export-test
lein test my-app.import-test
```

### Test Coverage

The test suite includes:

- **Core Tests**: Tests for the CLI interface and functions
- **Database Tests**: Tests for database CRUD operations, search functionality, and batch imports
- **Schema Tests**: Tests for database schema structure, constraints, and data integrity
- **API Tests**: Tests for REST API endpoints, request validation, and response formatting
- **Export Tests**: Tests for word formatting and file export functionality
- **Import Tests**: Tests for parsing and importing words from formatted text files

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
- Foreign key constraints

### API Testing Strategy

The API tests use Ring Mock to simulate HTTP requests and verify:
- Correct response status codes (200, 201, 400, 404, 500)
- Proper JSON response formatting
- Response headers (Content-Type, Content-Disposition)
- Request parameter validation
- Error handling
- Content types and download headers for export functionality

### Export/Import Testing Strategy

The export and import tests verify:
- Word formatting according to the specified format
- File writing and reading operations
- Error handling for file operations
- Parsing of formatted text content
- Extraction of word components (word, transcription, translation, etc.)
- Handling of edge cases (empty inputs, invalid formats)
- Multiple meanings per word

### Testing Approach

- Core functionality is tested with mocks for database interactions
- Database functionality is tested with a real SQLite database in a temporary location
- API endpoints are tested with simulated HTTP requests using ring-mock
- Export/import functionality is tested with temporary files that are created and deleted during tests
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
