(ns my-app.export-test
  (:require [clojure.test :refer :all]
            [my-app.export :as exp]
            [my-app.db :as db]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.io File)))

;; Тестовые данные в новом формате
(def test-word
  {:word "apple"
   :transcription "ˈæpl"
   :meanings [{:id 1
               :transcription "ˈæpl"
               :description "a round fruit with red skin"
               :translation "яблоко"
               :examples [{:text "I eat an apple every day."}
                          {:text "She picked apples from the tree."}]}]})

(def test-words
  [test-word
   {:word "banana"
    :transcription "bəˈnænə"
    :meanings [{:id 2
                :transcription "bəˈnænə"
                :description "a long curved fruit"
                :translation "банан"
                :examples [{:text "Monkeys love bananas."}]}]}])

;; Ожидаемый результат форматирования в новом формате
(def expected-word-format
  "- **apple** [ˈæpl]\n  - **Meaning**: a round fruit with red skin\n    - **Translation**: яблоко\n    - **Examples**:\n      - I eat an apple every day.\n      - She picked apples from the tree.\n\n")

(def expected-words_format
  (str "# Words\n\n"
       "- **apple** [ˈæpl]\n  - **Meaning**: a round fruit with red skin\n    - **Translation**: яблоко\n    - **Examples**:\n      - I eat an apple every day.\n      - She picked apples from the tree.\n\n"
       "- **banana** [bəˈnænə]\n  - **Meaning**: a long curved fruit\n    - **Translation**: банан\n    - **Examples**:\n      - Monkeys love bananas.\n\n"))

;; Mock database функции
(defn mock-get-all-words []
  test-words)

(defn mock-search-words [term]
  (if (= term "app")
    [(first test-words)]
    []))

;; Создаем временные файлы для тестирования
(defn temp-file [prefix]
  (doto (File/createTempFile prefix ".md")
    (.deleteOnExit)))

;; Test fixture для настройки mock функций базы данных
(defn with-mock-db [f]
  (with-redefs [db/get-all-words mock-get-all-words
                db/search-words mock-search-words]
    (f)))

(use-fixtures :each with-mock-db)

;; Тест форматирования слова
(deftest test-format-word-entry
  (testing "format-word-entry correctly formats a word entry"
    (let [formatted (exp/format-word-entry test-word)]
      (is (= expected-word-format formatted))))
  
  (testing "format-word-entry handles missing examples"
    (let [word-without-examples (update-in test-word [:meanings 0 :examples] (constantly []))
          formatted (exp/format-word-entry word-without-examples)]
      (is (str/includes? formatted "**apple**"))
      (is (str/includes? formatted "**Translation**: яблоко"))
      (is (str/ends-with? formatted "\n")))))

(deftest test-format-words-for-export
  (testing "format-words-for-export correctly formats multiple words"
    (let [formatted (exp/format-words-for-export test-words)]
      (is (= expected-words_format formatted)))))

(deftest test-export-words-to-file
  (testing "export-words-to-file writes words to file"
    (let [temp-file-path (.getPath (temp-file "export-test"))
          result (exp/export-words-to-file temp-file-path)
          file-content (slurp temp-file-path)]
      (is (= 2 result))
      (is (= expected-words_format file-content))))
  
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
      (is (= "# Words\n\n" file-content))))
  
  (testing "export-search-results-to-file handles errors gracefully"
    (with-redefs [spit (fn [_ _] (throw (Exception. "Test error")))]
      (let [result (exp/export-search-results-to-file "app" "test.txt")]
        (is (= 0 result)))))) 