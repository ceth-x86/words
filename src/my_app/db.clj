(ns my-app.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

;; Define the database spec
(def db-spec
  {:dbtype "sqlite"
   :dbname "resources/english_words.db"})

;; Function to create a new database and tables for English words
(defn init-db! []
  (let [ds (jdbc/get-datasource db-spec)]
    ;; Create words table
    (jdbc/execute! ds ["
      CREATE TABLE IF NOT EXISTS words (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        word TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(word)
      )
    "])
    
    ;; Create meanings table
    (jdbc/execute! ds ["
      CREATE TABLE IF NOT EXISTS meanings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        word_id INTEGER NOT NULL,
        transcription TEXT,
        description TEXT,
        translation TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
      )
    "])
    
    ;; Create examples table
    (jdbc/execute! ds ["
      CREATE TABLE IF NOT EXISTS examples (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        meaning_id INTEGER NOT NULL,
        text TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (meaning_id) REFERENCES meanings(id) ON DELETE CASCADE
      )
    "])
    
    ;; Create collections table
    (jdbc/execute! ds ["
      CREATE TABLE IF NOT EXISTS collections (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        description TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(name)
      )
    "])
    
    ;; Create words_collections junction table for many-to-many relationship
    (jdbc/execute! ds ["
      CREATE TABLE IF NOT EXISTS words_collections (
        word_id INTEGER NOT NULL,
        collection_id INTEGER NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (word_id, collection_id),
        FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE,
        FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE
      )
    "])))

;; Helper function to get or create a word
(defn- get-or-create-word! [tx word]
  (if-let [existing-word (jdbc/execute-one! tx ["SELECT id FROM words WHERE word = ?" word]
                                          {:builder-fn rs/as-unqualified-maps})]
    (:id existing-word)
    (do
      ;; Insert first
      (jdbc/execute! tx ["INSERT INTO words (word) VALUES (?)" word])
      ;; Then get the last inserted id
      (let [result (jdbc/execute-one! tx ["SELECT last_insert_rowid() as id"]
                                    {:builder-fn rs/as-unqualified-maps})]
        (:id result)))))

;; Helper function to add a meaning to a word
(defn- add-meaning! [tx word-id transcription description translation]
  (do
    ;; Insert first
    (jdbc/execute! tx ["
       INSERT INTO meanings (word_id, transcription, description, translation)
       VALUES (?, ?, ?, ?)
     " word-id transcription description translation])
    ;; Then get the last inserted id
    (let [result (jdbc/execute-one! tx ["SELECT last_insert_rowid() as id"]
                                  {:builder-fn rs/as-unqualified-maps})]
      (:id result))))

;; Helper function to add an example to a meaning
(defn- add-example! [tx meaning-id example-text]
  (jdbc/execute! tx ["
    INSERT INTO examples (meaning_id, text)
    VALUES (?, ?)
  " meaning-id example-text]))

;; Function to get all examples for a meaning by meaning_id
(defn get-meaning-examples [meaning-id]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      SELECT text
      FROM examples
      WHERE meaning_id = ?
      ORDER BY id
    " meaning-id]
                  {:builder-fn rs/as-unqualified-maps})))

;; Function to get all meanings for a word by word_id
(defn get-word-meanings [word-id]
  (let [ds (jdbc/get-datasource db-spec)
        meanings (jdbc/execute! ds ["
          SELECT id, transcription, description, translation
          FROM meanings
          WHERE word_id = ?
          ORDER BY id
        " word-id]
                              {:builder-fn rs/as-unqualified-maps})]
    (mapv #(assoc % :examples (get-meaning-examples (:id %))) meanings)))

;; Function to add a new word with a single meaning and examples
(defn insert-word! [word transcription description translation examples]
  (let [ds (jdbc/get-datasource db-spec)]
    (try
      (jdbc/with-transaction [tx ds]
        (let [word-id (get-or-create-word! tx word)
              meaning-id (add-meaning! tx word-id transcription description translation)]
          (when (not-empty examples)
            (doseq [example (clojure.string/split-lines examples)]
              (add-example! tx meaning-id example)))
          true))
      (catch Exception e
        (println (str "Error adding word: '" word "'. " (.getMessage e)))
        false))))

;; Function to add a new meaning to an existing word
(defn add-meaning-to-word! [word transcription description translation examples]
  (let [ds (jdbc/get-datasource db-spec)]
    (try
      (jdbc/with-transaction [tx ds]
        (if-let [word-record (jdbc/execute-one! tx ["SELECT id FROM words WHERE word = ?" word]
                                             {:builder-fn rs/as-unqualified-maps})]
          (let [word-id (:id word-record)
                meaning-id (add-meaning! tx word-id transcription description translation)]
            (when (not-empty examples)
              (doseq [example (clojure.string/split-lines examples)]
                (add-example! tx meaning-id example)))
            true)
          (do
            (println (str "Word '" word "' not found."))
            false)))
      (catch Exception e
        (println (str "Error adding meaning to word: '" word "'. " (.getMessage e)))
        false))))

;; Function to batch import words
(defn batch-import-words! [words-data]
  (let [ds (jdbc/get-datasource db-spec)]
    (try
      (jdbc/with-transaction [tx ds]
        (doseq [{:keys [word transcription description translation examples]} words-data]
          (try
            (let [word-id (get-or-create-word! tx word)
                  meaning-id (add-meaning! tx word-id transcription description translation)]
              (when (not-empty examples)
                (doseq [example (clojure.string/split-lines examples)]
                  (add-example! tx meaning-id example)))
              (println (str "Added word: '" word "'")))
            (catch Exception e
              (println (str "Error adding word: '" word "'. " (.getMessage e)))))))
      (catch Exception e
        (println "Error importing words:")
        (println (.getMessage e))))))

;; Function to get all words with their meanings and examples
(defn get-all-words []
  (let [ds (jdbc/get-datasource db-spec)
        words (jdbc/execute! ds ["SELECT id, word FROM words ORDER BY created_at DESC"]
                           {:builder-fn rs/as-unqualified-maps})]
    (mapv #(assoc % :meanings (get-word-meanings (:id %))) words)))

;; Function to get a specific word by word string
(defn get-word-by-word [word]
  (let [ds (jdbc/get-datasource db-spec)
        word-record (jdbc/execute-one! ds ["SELECT id, word FROM words WHERE word = ?" word]
                                     {:builder-fn rs/as-unqualified-maps})]
    (when word-record
      (assoc word-record :meanings (get-word-meanings (:id word-record))))))

;; Function to search words by English word or translation (partial match)
(defn search-words [search-term]
  (let [ds (jdbc/get-datasource db-spec)
        pattern (str "%" search-term "%")
        word-ids (jdbc/execute! ds ["
          SELECT DISTINCT w.id, w.word
          FROM words w
          LEFT JOIN meanings m ON w.id = m.word_id
          WHERE w.word LIKE ? OR m.translation LIKE ?
          ORDER BY w.word
        " pattern pattern]
                               {:builder-fn rs/as-unqualified-maps})]
    (mapv #(assoc % :meanings (get-word-meanings (:id %))) word-ids)))

;; Function to update a specific meaning
(defn update-meaning! [meaning-id transcription description translation]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      UPDATE meanings 
      SET transcription = ?, description = ?, translation = ? 
      WHERE id = ?
    " transcription description translation meaning-id])))

;; Function to delete a meaning
(defn delete-meaning! [meaning-id]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["DELETE FROM meanings WHERE id = ?" meaning-id])))

;; Function to delete a word and all its meanings
(defn delete-word! [word]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["DELETE FROM words WHERE word = ?" word])))

;; Function to add an example to a meaning
(defn add-example-to-meaning! [meaning-id example-text]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      INSERT INTO examples (meaning_id, text)
      VALUES (?, ?)
    " meaning-id example-text])))

;; Function to delete an example
(defn delete-example! [example-id]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["DELETE FROM examples WHERE id = ?" example-id])))

;; Function to count total words
(defn count-words []
  (let [ds (jdbc/get-datasource db-spec)
        result (jdbc/execute-one! ds ["SELECT COUNT(*) as count FROM words"]
                                 {:builder-fn rs/as-unqualified-maps})]
    (:count result)))

;; Function to count total meanings
(defn count-meanings []
  (let [ds (jdbc/get-datasource db-spec)
        result (jdbc/execute-one! ds ["SELECT COUNT(*) as count FROM meanings"]
                                 {:builder-fn rs/as-unqualified-maps})]
    (:count result)))

;; Function to count total examples
(defn count-examples []
  (let [ds (jdbc/get-datasource db-spec)
        result (jdbc/execute-one! ds ["SELECT COUNT(*) as count FROM examples"]
                                 {:builder-fn rs/as-unqualified-maps})]
    (:count result)))

;; Collections functions

;; Function to create a new collection
(defn create-collection! [name description]
  (let [ds (jdbc/get-datasource db-spec)]
    (try
      (jdbc/execute! ds ["
        INSERT INTO collections (name, description) 
        VALUES (?, ?)
      " name description])
      true
      (catch Exception e
        (println (str "Error creating collection: '" name "'. " (.getMessage e)))
        false))))

;; Function to get all collections
(defn get-all-collections []
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      SELECT id, name, description, created_at
      FROM collections
      ORDER BY name
    "] {:builder-fn rs/as-unqualified-maps})))

;; Function to get a collection by ID
(defn get-collection-by-id [collection-id]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute-one! ds ["
      SELECT id, name, description, created_at
      FROM collections
      WHERE id = ?
    " collection-id] {:builder-fn rs/as-unqualified-maps})))

;; Function to get a collection by name
(defn get-collection-by-name [name]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute-one! ds ["
      SELECT id, name, description, created_at
      FROM collections
      WHERE name = ?
    " name] {:builder-fn rs/as-unqualified-maps})))

;; Function to update a collection
(defn update-collection! [collection-id name description]
  (let [ds (jdbc/get-datasource db-spec)]
    (try
      (jdbc/execute! ds ["
        UPDATE collections 
        SET name = ?, description = ?
        WHERE id = ?
      " name description collection-id])
      true
      (catch Exception e
        (println (str "Error updating collection ID: " collection-id ". " (.getMessage e)))
        false))))

;; Function to delete a collection
(defn delete-collection! [collection-id]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      DELETE FROM collections 
      WHERE id = ?
    " collection-id])))

;; Function to add a word to a collection
(defn add-word-to-collection! [word-id collection-id]
  (let [ds (jdbc/get-datasource db-spec)]
    (try
      (jdbc/execute! ds ["
        INSERT INTO words_collections (word_id, collection_id)
        VALUES (?, ?)
      " word-id collection-id])
      true
      (catch Exception e
        (println (str "Error adding word to collection. " (.getMessage e)))
        false))))

;; Function to add a word (by word string) to a collection
(defn add-word-string-to-collection! [word collection-id]
  (let [ds (jdbc/get-datasource db-spec)
        word-record (jdbc/execute-one! ds ["SELECT id FROM words WHERE word = ?" word]
                                        {:builder-fn rs/as-unqualified-maps})]
    (if word-record
      (add-word-to-collection! (:id word-record) collection-id)
      (do
        (println (str "Word '" word "' not found."))
        false))))

;; Function to remove a word from a collection
(defn remove-word-from-collection! [word-id collection-id]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      DELETE FROM words_collections
      WHERE word_id = ? AND collection_id = ?
    " word-id collection-id])))

;; Function to remove a word (by word string) from a collection
(defn remove-word-string-from-collection! [word collection-id]
  (let [ds (jdbc/get-datasource db-spec)
        word-record (jdbc/execute-one! ds ["SELECT id FROM words WHERE word = ?" word]
                                        {:builder-fn rs/as-unqualified-maps})]
    (if word-record
      (remove-word-from-collection! (:id word-record) collection-id)
      (do
        (println (str "Word '" word "' not found."))
        false))))

;; Function to get all words in a collection
(defn get-collection-words [collection-id]
  (let [ds (jdbc/get-datasource db-spec)
        word-ids (jdbc/execute! ds ["
          SELECT w.id, w.word
          FROM words w
          JOIN words_collections wc ON w.id = wc.word_id
          WHERE wc.collection_id = ?
          ORDER BY w.word
        " collection-id] {:builder-fn rs/as-unqualified-maps})]
    (mapv #(assoc % :meanings (get-word-meanings (:id %))) word-ids)))

;; Function to count words in a collection
(defn count-collection-words [collection-id]
  (let [ds (jdbc/get-datasource db-spec)
        result (jdbc/execute-one! ds ["
          SELECT COUNT(*) as count 
          FROM words_collections 
          WHERE collection_id = ?
        " collection-id] {:builder-fn rs/as-unqualified-maps})]
    (:count result)))

;; Function to check if a word is in a collection
(defn word-in-collection? [word-id collection-id]
  (let [ds (jdbc/get-datasource db-spec)
        result (jdbc/execute-one! ds ["
          SELECT COUNT(*) as count 
          FROM words_collections 
          WHERE word_id = ? AND collection_id = ?
        " word-id collection-id] {:builder-fn rs/as-unqualified-maps})]
    (> (:count result) 0)))

;; Function to get all collections a word belongs to
(defn get-word-collections [word-id]
  (let [ds (jdbc/get-datasource db-spec)]
    (jdbc/execute! ds ["
      SELECT c.id, c.name, c.description
      FROM collections c
      JOIN words_collections wc ON c.id = wc.collection_id
      WHERE wc.word_id = ?
      ORDER BY c.name
    " word-id] {:builder-fn rs/as-unqualified-maps})))