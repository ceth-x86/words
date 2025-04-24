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
    (let [test-word {:word "test"
                    :transcription "tɛst"
                    :description "A procedure for testing something"
                    :translation "тест"
                    :examples "This is a test example."}
          output (capture-stdout #(format-word test-word))]
      (is (str/includes? output "Word: test"))
      (is (str/includes? output "Transcription: tɛst"))
      (is (str/includes? output "Description: A procedure for testing something"))
      (is (str/includes? output "Translation: тест"))
      (is (str/includes? output "Examples: This is a test example.")))))

;; Mock the DB interactions
(defn with-mocked-db [f]
  (with-redefs [my-app.db/init-db! (fn [] nil)
                my-app.db/get-all-words (fn [] 
                                         [{:word "apple" 
                                           :transcription "ˈæpl" 
                                           :description "A fruit" 
                                           :translation "яблоко" 
                                           :examples "I ate an apple."
                                           :created_at "2023-01-01T12:00:00Z"}])
                my-app.db/insert-word! (fn [& args] nil)
                my-app.db/get-word-by-word (fn [word] 
                                            (when (= word "apple")
                                              {:word "apple" 
                                               :transcription "ˈæpl" 
                                               :description "A fruit" 
                                               :translation "яблоко" 
                                               :examples "I ate an apple."
                                               :created_at "2023-01-01T12:00:00Z"}))
                my-app.db/update-word! (fn [& args] nil)
                my-app.db/delete-word! (fn [& args] nil)
                my-app.db/search-words (fn [term] 
                                        (if (= term "apple")
                                          [{:word "apple" 
                                            :transcription "ˈæpl" 
                                            :description "A fruit" 
                                            :translation "яблоко" 
                                            :examples "I ate an apple."
                                            :created_at "2023-01-01T12:00:00Z"}]
                                          []))
                my-app.export/export-words-to-file (fn [file-path] 1)
                my-app.export/export-search-results-to-file (fn [term file-path] 1)
                my-app.import/import-words-from-file (fn [file-path] 1)]
    (f)))

(use-fixtures :each with-mocked-db)

(deftest add-word-test
  (testing "add-word function displays correct message"
    (let [output (capture-stdout #(add-word "test" "tɛst" "A test" "тест" "Example"))]
      (is (str/includes? output "Adding word: \"test\""))
      (is (str/includes? output "Word successfully added")))))

(deftest show-words-test
  (testing "show-words displays all words"
    (let [output (capture-stdout show-words)]
      (is (str/includes? output "List of all English words:"))
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

(deftest update-word-test
  (testing "update-word function displays correct message for existing word"
    (let [output (capture-stdout #(update-word "apple" "ˈæpl" "A fruit" "яблоко" "Updated example."))]
      (is (str/includes? output "Updating word: \"apple\""))
      (is (str/includes? output "Word successfully updated"))))
  
  (testing "update-word function displays not found message for non-existent word"
    (let [output (capture-stdout #(update-word "nonexistent" "test" "test" "test" "test"))]
      (is (str/includes? output "Updating word: \"nonexistent\""))
      (is (str/includes? output "Word \"nonexistent\" not found")))))

(deftest delete-word-test
  (testing "delete-word function displays correct message for existing word"
    (let [output (capture-stdout #(delete-word "apple"))]
      (is (str/includes? output "Deleting word: \"apple\""))
      (is (str/includes? output "Word successfully deleted"))))
  
  (testing "delete-word function displays not found message for non-existent word"
    (let [output (capture-stdout #(delete-word "nonexistent"))]
      (is (str/includes? output "Deleting word: \"nonexistent\""))
      (is (str/includes? output "Word \"nonexistent\" not found")))))

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
