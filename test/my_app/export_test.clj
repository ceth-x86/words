(ns my-app.export-test
  (:require [clojure.test :refer :all]
            [my-app.export :as exp]
            [my-app.db :as db]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.io File)))

;; Test data
(def test-word
  {:word "apple"
   :transcription "ˈæpl"
   :description "A round fruit"
   :translation "яблоко"
   :examples "I ate an apple.\nShe likes green apples."})

(def test-words
  [test-word
   {:word "banana"
    :transcription "bəˈnænə"
    :description "A long curved fruit"
    :translation "банан"
    :examples "Monkeys eat bananas."}])

;; Expected output for formatting tests
(def expected-word-format
  "**apple** /ˈæpl/ - A round fruit\n(яблоко)\n- I ate an apple.\n- She likes green apples.\n")

(def expected-words-format
  (str "**apple** /ˈæpl/ - A round fruit\n(яблоко)\n- I ate an apple.\n- She likes green apples.\n\n"
       "**banana** /bəˈnænə/ - A long curved fruit\n(банан)\n- Monkeys eat bananas.\n"))

;; Mock database functions
(defn mock-get-all-words []
  test-words)

(defn mock-search-words [term]
  (if (= term "app")
    [(first test-words)]
    []))

;; Create temporary files for testing
(defn temp-file [prefix]
  (doto (File/createTempFile prefix ".txt")
    (.deleteOnExit)))

;; Test fixture to setup mock database functions
(defn with-mock-db [f]
  (with-redefs [db/get-all-words mock-get-all-words
                db/search-words mock-search-words]
    (f)))

(use-fixtures :each with-mock-db)

;; Test word formatting
(deftest test-format-word-entry
  (testing "format-word-entry correctly formats a word entry"
    (let [formatted (exp/format-word-entry test-word)]
      (is (= expected-word-format formatted))))
  
  (testing "format-word-entry handles missing examples"
    (let [word-without-examples (dissoc test-word :examples)
          formatted (exp/format-word-entry word-without-examples)]
      (is (str/includes? formatted "**apple**"))
      (is (str/includes? formatted "(яблоко)"))
      (is (str/ends-with? formatted "\n")))))

(deftest test-format-words-for-export
  (testing "format-words-for-export correctly formats multiple words"
    (let [formatted (exp/format-words-for-export test-words)]
      (is (= expected-words-format formatted)))))

(deftest test-export-words-to-file
  (testing "export-words-to-file writes words to file"
    (let [temp-file-path (.getPath (temp-file "export-test"))
          result (exp/export-words-to-file temp-file-path)
          file-content (slurp temp-file-path)]
      (is (= 2 result))
      (is (= expected-words-format file-content))))
  
  (testing "export-words-to-file handles errors gracefully"
    (with-redefs [spit (fn [_ _] (throw (Exception. "Test error")))]
      (let [result (exp/export-words-to-file "test.txt")]
        (is (= 0 result))))))

(deftest test-export-search-results-to-file
  (testing "export-search-results-to-file writes search results to file"
    (let [temp-file-path (.getPath (temp-file "search-test"))
          result (exp/export-search-results-to-file "app" temp-file-path)
          file-content (slurp temp-file-path)]
      (is (= 1 result))
      (is (str/includes? file-content "**apple**"))
      (is (not (str/includes? file-content "**banana**")))))
  
  (testing "export-search-results-to-file handles empty results"
    (let [temp-file-path (.getPath (temp-file "empty-search"))
          result (exp/export-search-results-to-file "xyz" temp-file-path)
          file-content (slurp temp-file-path)]
      (is (= 0 result))
      (is (= "" file-content))))
  
  (testing "export-search-results-to-file handles errors gracefully"
    (with-redefs [spit (fn [_ _] (throw (Exception. "Test error")))]
      (let [result (exp/export-search-results-to-file "app" "test.txt")]
        (is (= 0 result)))))) 