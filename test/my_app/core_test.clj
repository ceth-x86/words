(ns my-app.core-test
  (:require [clojure.test :refer :all]
            [my-app.core :refer :all]
            [my-app.export :as exp]
            [clojure.string :as str]))

;; Test helpers
(defn capture-stdout
  "Capture stdout output from executing the function"
  [f]
  (let [out (java.io.StringWriter.)
        original-out *out*]
    (with-bindings {#'*out* out}
      (f)
      (str out))))

;; Core function tests

(deftest show-usage-test
  (testing "show-usage prints expected help text"
    (let [output (capture-stdout show-usage)]
      (is (str/includes? output "Usage:"))
      (is (str/includes? output "Initialize the database"))
      (is (str/includes? output "Add a new word")))))

(deftest format-word-test
  (testing "format-word displays word fields correctly"
    (let [test-word {:id 1 
                     :word "test"
                     :meanings [{:id 1
                                 :transcription "tɛst"
                                 :description "A procedure for testing something"
                                 :translation "тест"
                                 :examples [{:text "This is a test example."}]}]}
          output (capture-stdout #(format-word test-word))]
      (is (str/includes? output "Word: test"))
      (is (str/includes? output "Meanings: 1"))
      (is (str/includes? output "Transcription: tɛst"))
      (is (str/includes? output "Description: A procedure for testing something"))
      (is (str/includes? output "Translation: тест"))
      (is (str/includes? output "This is a test example.")))))

;; Mock the DB interactions
(defn with-mocked-db [f]
  (with-redefs [my-app.db/init-db! (fn [] nil)
                my-app.db/get-all-words (fn [] 
                                         [{:id 1 
                                           :word "apple"
                                           :meanings [{:id 1
                                                       :transcription "ˈæpl" 
                                                       :description "A fruit" 
                                                       :translation "яблоко" 
                                                       :examples [{:text "I ate an apple."}]}]}])
                my-app.db/insert-word! (fn [& args] true)
                my-app.db/add-meaning-to-word! (fn [& args] true)
                my-app.db/update-meaning! (fn [& args] true)
                my-app.db/delete-meaning! (fn [& args] true)
                my-app.db/get-word-by-word (fn [word] 
                                            (when (= word "apple")
                                              {:id 1 
                                               :word "apple"
                                               :meanings [{:id 1
                                                           :transcription "ˈæpl" 
                                                           :description "A fruit" 
                                                           :translation "яблоко" 
                                                           :examples [{:text "I ate an apple."}]}]}))
                my-app.db/delete-word! (fn [& args] nil)
                my-app.db/add-example-to-meaning! (fn [& args] true)
                my-app.db/count-words (fn [] 1)
                my-app.db/count-meanings (fn [] 2)
                my-app.db/count-examples (fn [] 3)
                my-app.db/search-words (fn [term] 
                                        (if (= term "apple")
                                          [{:id 1 
                                            :word "apple"
                                            :meanings [{:id 1
                                                        :transcription "ˈæpl" 
                                                        :description "A fruit" 
                                                        :translation "яблоко" 
                                                        :examples [{:text "I ate an apple."}]}]}]
                                          []))
                ;; Collection mocks
                my-app.db/get-all-collections (fn [] 
                                              [{:id 1 
                                                :name "Test Collection"
                                                :description "A collection for testing"
                                                :created_at "2023-01-01 12:00:00"}])
                my-app.db/get-collection-by-id (fn [id] 
                                               (when (= id "1")
                                                 {:id 1 
                                                  :name "Test Collection"
                                                  :description "A collection for testing"
                                                  :created_at "2023-01-01 12:00:00"}))
                my-app.db/create-collection! (fn [& args] true)
                my-app.db/update-collection! (fn [& args] true)
                my-app.db/delete-collection! (fn [& args] nil)
                my-app.db/get-collection-words (fn [id] 
                                               (when (= id "1")
                                                 [{:id 1 
                                                   :word "apple"
                                                   :meanings [{:id 1
                                                               :transcription "ˈæpl" 
                                                               :description "A fruit" 
                                                               :translation "яблоко" 
                                                               :examples [{:text "I ate an apple."}]}]}]))
                my-app.db/add-word-string-to-collection! (fn [& args] true)
                my-app.db/remove-word-string-from-collection! (fn [& args] true)
                my-app.db/get-word-collections (fn [word-id] 
                                               (when (= word-id 1)
                                                 [{:id 1 
                                                   :name "Test Collection"
                                                   :description "A collection for testing"
                                                   :created_at "2023-01-01 12:00:00"}]))
                my-app.export/export-words-to-file (fn [file-path] 1)
                my-app.export/export-search-results-to-file (fn [term file-path] 1)
                my-app.import/import-words-from-file (fn [file-path] 1)]
    (f)))

(use-fixtures :each with-mocked-db)

(deftest add-word-test
  (testing "add-word function displays correct message"
    (let [output (capture-stdout #(add-word "test" "tɛst" "A test" "тест" "Example"))]
      (is (str/includes? output "Adding word: \"test\""))
      (is (str/includes? output "Word or meaning successfully added")))))

(deftest add-meaning-test
  (testing "add-meaning function displays correct message"
    (let [output (capture-stdout #(add-meaning "apple" "tɛst" "Another meaning" "тест" "Example"))]
      (is (str/includes? output "Adding meaning to word: \"apple\""))
      (is (str/includes? output "Meaning successfully added")))))

(deftest show-words-test
  (testing "show-words displays all words"
    (let [output (capture-stdout show-words)]
      (is (str/includes? output "List of all English words:"))
      (is (str/includes? output "Total words: 1"))
      (is (str/includes? output "Total meanings: 2"))
      (is (str/includes? output "Total examples: 3"))
      (is (str/includes? output "Word: apple")))))

(deftest get-word-test
  (testing "get-word displays word information when found"
    (let [output (capture-stdout #(get-word "apple"))]
      (is (str/includes? output "Getting information about word: \"apple\""))
      (is (str/includes? output "Word: apple"))))
  
  (testing "get-word displays not found message when word doesn't exist"
    (let [output (capture-stdout #(get-word "nonexistent"))]
      (is (str/includes? output "Getting information about word: \"nonexistent\""))
      (is (str/includes? output "Word \"nonexistent\" not found")))))

(deftest update-meaning-test
  (testing "update-meaning function displays correct message"
    (let [output (capture-stdout #(update-meaning "1" "ˈæpl" "A fruit" "яблоко"))]
      (is (str/includes? output "Updating meaning: \"1\""))
      (is (str/includes? output "Meaning successfully updated")))))

(deftest delete-meaning-test
  (testing "delete-meaning function displays correct message"
    (let [output (capture-stdout #(delete-meaning "1"))]
      (is (str/includes? output "Deleting meaning: \"1\""))
      (is (str/includes? output "Meaning successfully deleted")))))

(deftest delete-word-test
  (testing "delete-word function displays correct message for existing word"
    (let [output (capture-stdout #(delete-word "apple"))]
      (is (str/includes? output "Deleting word: \"apple\""))
      (is (str/includes? output "Word and all its meanings successfully deleted"))))
  
  (testing "delete-word function displays not found message for non-existent word"
    (let [output (capture-stdout #(delete-word "nonexistent"))]
      (is (str/includes? output "Deleting word: \"nonexistent\""))
      (is (str/includes? output "Word \"nonexistent\" not found")))))

(deftest add-example-test
  (testing "add-example function displays correct message"
    (let [output (capture-stdout #(add-example "1" "This is another example."))]
      (is (str/includes? output "Adding example to meaning: \"1\""))
      (is (str/includes? output "Example successfully added")))))

(deftest search-words-test
  (testing "search-words function displays results when found"
    (let [output (capture-stdout #(search-words "apple"))]
      (is (str/includes? output "Searching for words with query: \"apple\""))
      (is (str/includes? output "Word: apple"))))
  
  (testing "search-words function displays not found message when nothing matches"
    (let [output (capture-stdout #(search-words "nonexistent"))]
      (is (str/includes? output "Searching for words with query: \"nonexistent\""))
      (is (str/includes? output "Nothing found")))))

(deftest import-words-test
  (testing "import-words function displays correct message"
    (let [output (capture-stdout #(import-words "test.txt"))]
      (is (str/includes? output "Importing words from file: test.txt"))
      (is (str/includes? output "Import process completed")))))

(deftest export-words-test
  (testing "export-words function displays correct message"
    (let [output (capture-stdout #(export-words "test.txt"))]
      (is (str/includes? output "Exporting all words to file: test.txt"))
      (is (str/includes? output "Export process completed")))))

(deftest export-search-results-test
  (testing "export-search-results function displays correct message"
    (let [output (capture-stdout #(export-search-results "apple" "test.txt"))]
      (is (str/includes? output "Exporting search results \"apple\" to file: test.txt"))
      (is (str/includes? output "Export process completed")))))

;; Collection function tests

(deftest format-collection-test
  (testing "format-collection displays collection fields correctly"
    (let [test-collection {:id 1 
                           :name "Test Collection"
                           :description "A collection for testing"
                           :created_at "2023-01-01 12:00:00"}
          output (capture-stdout #(format-collection test-collection))]
      (is (str/includes? output "Collection: Test Collection"))
      (is (str/includes? output "Description: A collection for testing"))
      (is (str/includes? output "Created at: 2023-01-01 12:00:00")))))

(deftest show-collections-test
  (testing "show-collections displays all collections"
    (let [output (capture-stdout show-collections)]
      (is (str/includes? output "List of all collections:"))
      (is (str/includes? output "Total collections: 1"))
      (is (str/includes? output "Collection: Test Collection")))))

(deftest create-collection-test
  (testing "create-collection function displays correct message"
    (let [output (capture-stdout #(create-collection "New Collection" "A new test collection"))]
      (is (str/includes? output "Creating collection: \"New Collection\""))
      (is (str/includes? output "Collection successfully created")))))

(deftest get-collection-test
  (testing "get-collection displays collection information when found"
    (let [output (capture-stdout #(get-collection "1"))]
      (is (str/includes? output "Getting information about collection ID: 1"))
      (is (str/includes? output "Collection: Test Collection"))
      (is (str/includes? output "Words in this collection: 1"))))
  
  (testing "get-collection displays not found message when collection doesn't exist"
    (let [output (capture-stdout #(get-collection "999"))]
      (is (str/includes? output "Getting information about collection ID: 999"))
      (is (str/includes? output "Collection ID 999 not found")))))

(deftest update-collection-test
  (testing "update-collection function displays correct message"
    (let [output (capture-stdout #(update-collection "1" "Updated Collection" "Updated description"))]
      (is (str/includes? output "Updating collection ID: 1"))
      (is (str/includes? output "Collection successfully updated")))))

(deftest delete-collection-test
  (testing "delete-collection function displays correct message"
    (let [output (capture-stdout #(delete-collection "1"))]
      (is (str/includes? output "Deleting collection ID: 1"))
      (is (str/includes? output "Collection successfully deleted")))))

(deftest add-word-to-collection-test
  (testing "add-word-to-collection function displays correct message"
    (let [output (capture-stdout #(add-word-to-collection "apple" "1"))]
      (is (str/includes? output "Adding word \"apple\" to collection ID: 1"))
      (is (str/includes? output "Word successfully added to collection")))))

(deftest remove-word-from-collection-test
  (testing "remove-word-from-collection function displays correct message"
    (let [output (capture-stdout #(remove-word-from-collection "apple" "1"))]
      (is (str/includes? output "Removing word \"apple\" from collection ID: 1"))
      (is (str/includes? output "Word successfully removed from collection")))))

(deftest get-word-collections-test
  (testing "get-word-collections displays collections for word when found"
    (let [output (capture-stdout #(get-word-collections "apple"))]
      (is (str/includes? output "Getting collections for word: \"apple\""))
      (is (str/includes? output "Collections containing word \"apple\":"))
      (is (str/includes? output "Collection: Test Collection"))))
  
  (testing "get-word-collections displays not found message when word doesn't exist"
    (let [output (capture-stdout #(get-word-collections "nonexistent"))]
      (is (str/includes? output "Getting collections for word: \"nonexistent\""))
      (is (str/includes? output "Word \"nonexistent\" not found")))))
