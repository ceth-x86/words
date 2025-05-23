(ns my-app.core
  (:require [my-app.db :as db]
            [my-app.import :as imp]
            [my-app.export :as exp]
            [my-app.api :as api]
            [ring.adapter.jetty :as jetty]
            [clojure.string :as str])
  (:gen-class))

(defn show-usage []
  (println "Usage:")
  (println "  lein run init                   - Initialize the database")
  (println "  lein run add \"word\" \"transcription\" \"description\" \"translation\" \"examples\" - Add a new word or meaning")
  (println "  lein run add-meaning \"word\" \"transcription\" \"description\" \"translation\" \"examples\" - Add a new meaning to existing word")
  (println "  lein run show                   - Show all words")
  (println "  lein run search \"term\"          - Search for words")
  (println "  lein run get \"word\"             - Show a specific word")
  (println "  lein run update-meaning \"meaning_id\" \"transcription\" \"description\" \"translation\" - Update a meaning")
  (println "  lein run delete-meaning \"meaning_id\" - Delete a meaning")
  (println "  lein run delete \"word\"          - Delete a word and all its meanings")
  (println "  lein run add-example \"meaning_id\" \"example\" - Add an example to a meaning")
  (println "  lein run import \"file_path\"     - Import words from a file")
  (println "  lein run export \"file_path\"     - Export all words to a file")
  (println "  lein run export-search \"term\" \"file_path\" - Export search results to a file")
  (println "  lein run server [port]          - Start a web server with REST API (default port 3000)"))

(defn init-database []
  (println "Initializing English words database...")
  (db/init-db!)
  (println "Database successfully initialized."))

(defn add-word [word transcription description translation examples]
  (println (str "Adding word: \"" word "\""))
  (db/insert-word! word transcription description translation examples)
  (println "Word or meaning successfully added."))

(defn add-meaning [word transcription description translation examples]
  (println (str "Adding meaning to word: \"" word "\""))
  (db/add-meaning-to-word! word transcription description translation examples)
  (println "Meaning successfully added."))

(defn format-example [example]
  (println (str "  - " (:text example))))

(defn format-meaning [meaning]
  (println (str "  Meaning ID: " (:id meaning)))
  (println (str "  Transcription: " (:transcription meaning)))
  (println (str "  Description: " (:description meaning)))
  (println (str "  Translation: " (:translation meaning)))
  (println "  Examples:")
  (if (empty? (:examples meaning))
    (println "    No examples")
    (doseq [example (:examples meaning)]
      (format-example example)))
  (println))

(defn format-word [word]
  (println (str "Word: " (:word word) " (ID: " (:id word) ")"))
  (println (str "Meanings: " (count (:meanings word))))
  (doseq [meaning (:meanings word)]
    (format-meaning meaning))
  (println "------------------------"))

(defn show-words []
  (println "List of all English words:")
  (let [words (db/get-all-words)]
    (if (empty? words)
      (println "Database is empty.")
      (do
        (println (str "Total words: " (count words) 
                     ", Total meanings: " (db/count-meanings)
                     ", Total examples: " (db/count-examples)))
        (doseq [word words]
          (format-word word))))))

(defn get-word [word-str]
  (println (str "Getting information about word: \"" word-str "\""))
  (if-let [word (db/get-word-by-word word-str)]
    (format-word word)
    (println (str "Word \"" word-str "\" not found."))))

(defn update-meaning [meaning-id transcription description translation]
  (println (str "Updating meaning: \"" meaning-id "\""))
  (db/update-meaning! meaning-id transcription description translation)
  (println "Meaning successfully updated."))

(defn delete-meaning [meaning-id]
  (println (str "Deleting meaning: \"" meaning-id "\""))
  (db/delete-meaning! meaning-id)
  (println "Meaning successfully deleted."))

(defn delete-word [word]
  (println (str "Deleting word: \"" word "\""))
  (if (db/get-word-by-word word)
    (do
      (db/delete-word! word)
      (println "Word and all its meanings successfully deleted."))
    (println (str "Word \"" word "\" not found."))))

(defn add-example [meaning-id example-text]
  (println (str "Adding example to meaning: \"" meaning-id "\""))
  (db/add-example-to-meaning! meaning-id example-text)
  (println "Example successfully added."))

(defn search-words [term]
  (println (str "Searching for words with query: \"" term "\""))
  (let [results (db/search-words term)]
    (if (empty? results)
      (println "Nothing found.")
      (do
        (println (str "Found " (count results) " words:"))
        (doseq [word results]
          (format-word word))))))

(defn import-words [file-path]
  (println (str "Importing words from file: " file-path))
  (let [count (imp/import-words-from-file file-path)]
    (println (str "Import process completed. Successfully imported words: " count))))

(defn export-words [file-path]
  (println (str "Exporting all words to file: " file-path))
  (let [count (exp/export-words-to-file file-path)]
    (println (str "Export process completed. Successfully exported words: " count))))

(defn export-search-results [term file-path]
  (println (str "Exporting search results \"" term "\" to file: " file-path))
  (let [count (exp/export-search-results-to-file term file-path)]
    (println (str "Export process completed. Successfully exported words: " count))))

(defn start-server [port]
  (let [port-num (if port (Integer/parseInt port) 3000)]
    (println (str "Starting web server on port " port-num "..."))
    (println "API available at: http://localhost:" port-num "/api/")
    (println "Press Ctrl+C to stop the server.")
    (db/init-db!) ; initialize the database when starting the server
    (jetty/run-jetty api/app {:port port-num :join? true})))

(defn format-collection [collection]
  (println (str "Collection: " (:name collection) " (ID: " (:id collection) ")"))
  (println (str "Description: " (:description collection)))
  (println (str "Created at: " (:created_at collection)))
  (println "------------------------"))

(defn show-collections []
  (println "List of all collections:")
  (let [collections (db/get-all-collections)]
    (if (empty? collections)
      (println "No collections found.")
      (do
        (println (str "Total collections: " (count collections)))
        (doseq [collection collections]
          (format-collection collection))))))

(defn create-collection [name description]
  (println (str "Creating collection: \"" name "\""))
  (if (db/create-collection! name description)
    (println "Collection successfully created.")
    (println "Failed to create collection.")))

(defn get-collection [collection-id]
  (println (str "Getting information about collection ID: " collection-id))
  (if-let [collection (db/get-collection-by-id collection-id)]
    (let [words (db/get-collection-words collection-id)]
      (format-collection collection)
      (println (str "Words in this collection: " (count words)))
      (if (empty? words)
        (println "No words in this collection.")
        (doseq [word words]
          (format-word word))))
    (println (str "Collection ID " collection-id " not found."))))

(defn update-collection [collection-id name description]
  (println (str "Updating collection ID: " collection-id))
  (if (db/update-collection! collection-id name description)
    (println "Collection successfully updated.")
    (println "Failed to update collection.")))

(defn delete-collection [collection-id]
  (println (str "Deleting collection ID: " collection-id))
  (db/delete-collection! collection-id)
  (println "Collection successfully deleted."))

(defn add-word-to-collection [word collection-id]
  (println (str "Adding word \"" word "\" to collection ID: " collection-id))
  (if (db/add-word-string-to-collection! word collection-id)
    (println "Word successfully added to collection.")
    (println "Failed to add word to collection.")))

(defn remove-word-from-collection [word collection-id]
  (println (str "Removing word \"" word "\" from collection ID: " collection-id))
  (if (db/remove-word-string-from-collection! word collection-id)
    (println "Word successfully removed from collection.")
    (println "Failed to remove word from collection.")))

(defn get-word-collections [word]
  (println (str "Getting collections for word: \"" word "\""))
  (if-let [word-obj (db/get-word-by-word word)]
    (let [collections (db/get-word-collections (:id word-obj))]
      (if (empty? collections)
        (println "This word doesn't belong to any collections.")
        (do
          (println (str "Collections containing word \"" word "\":"))
          (doseq [collection collections]
            (format-collection collection)))))
    (println (str "Word \"" word "\" not found."))))

(defn -main
  "Application entry point"
  [& args]
  (db/init-db!) ; always initialize the database at startup
  
  (cond
    (empty? args)
    (show-usage)
    
    (= (first args) "init")
    (init-database)
    
    (= (first args) "add")
    (if (>= (count args) 6)
      (add-word (nth args 1) (nth args 2) (nth args 3) (nth args 4) (nth args 5))
      (println "Error: The add command requires a word, transcription, description, translation, and examples."))
    
    (= (first args) "add-meaning")
    (if (>= (count args) 6)
      (add-meaning (nth args 1) (nth args 2) (nth args 3) (nth args 4) (nth args 5))
      (println "Error: The add-meaning command requires a word, transcription, description, translation, and examples."))
    
    (= (first args) "show")
    (show-words)
    
    (= (first args) "get")
    (if (>= (count args) 2)
      (get-word (nth args 1))
      (println "Error: The get command requires a word."))
    
    (= (first args) "update-meaning")
    (if (>= (count args) 5)
      (update-meaning (nth args 1) (nth args 2) (nth args 3) (nth args 4))
      (println "Error: The update-meaning command requires a meaning_id, transcription, description, and translation."))
    
    (= (first args) "delete-meaning")
    (if (>= (count args) 2)
      (delete-meaning (nth args 1))
      (println "Error: The delete-meaning command requires a meaning_id."))
    
    (= (first args) "delete")
    (if (>= (count args) 2)
      (delete-word (nth args 1))
      (println "Error: The delete command requires a word."))
    
    (= (first args) "add-example")
    (if (>= (count args) 3)
      (add-example (nth args 1) (nth args 2))
      (println "Error: The add-example command requires a meaning_id and example text."))
    
    (= (first args) "search")
    (if (>= (count args) 2)
      (search-words (nth args 1))
      (println "Error: The search command requires a search query."))
    
    (= (first args) "import")
    (if (>= (count args) 2)
      (import-words (nth args 1))
      (println "Error: The import command requires a file path."))
    
    (= (first args) "export")
    (if (>= (count args) 2)
      (export-words (nth args 1))
      (println "Error: The export command requires a file path."))

    (= (first args) "export-search")
    (if (>= (count args) 3)
      (export-search-results (nth args 1) (nth args 2))
      (println "Error: The export-search command requires a search query and a file path."))
    
    (= (first args) "collections")
    (show-collections)
    
    (= (first args) "create-collection")
    (if (>= (count args) 3)
      (create-collection (nth args 1) (nth args 2))
      (println "Error: The create-collection command requires a name and description."))
    
    (= (first args) "get-collection")
    (if (>= (count args) 2)
      (get-collection (nth args 1))
      (println "Error: The get-collection command requires a collection_id."))
    
    (= (first args) "update-collection")
    (if (>= (count args) 4)
      (update-collection (nth args 1) (nth args 2) (nth args 3))
      (println "Error: The update-collection command requires a collection_id, name, and description."))
    
    (= (first args) "delete-collection")
    (if (>= (count args) 2)
      (delete-collection (nth args 1))
      (println "Error: The delete-collection command requires a collection_id."))
    
    (= (first args) "add-to-collection")
    (if (>= (count args) 3)
      (add-word-to-collection (nth args 1) (nth args 2))
      (println "Error: The add-to-collection command requires a word and a collection_id."))
    
    (= (first args) "remove-from-collection")
    (if (>= (count args) 3)
      (remove-word-from-collection (nth args 1) (nth args 2))
      (println "Error: The remove-from-collection command requires a word and a collection_id."))
    
    (= (first args) "word-collections")
    (if (>= (count args) 2)
      (get-word-collections (nth args 1))
      (println "Error: The word-collections command requires a word."))
    
    (= (first args) "server")
    (start-server (second args))
    
    :else
    (do
      (println "Unknown command.")
      (show-usage))))
