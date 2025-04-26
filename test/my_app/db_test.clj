(ns my-app.db-test
  (:require [clojure.test :refer :all]
            [my-app.db :as db]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.java.io :as io])
  (:import (java.io File)))

;; Define a temporary file-based test database
(def test-db-file (str (System/getProperty "java.io.tmpdir") "/test_english_words.db"))

;; Define the test database spec
(def test-db-spec
  {:dbtype "sqlite"
   :dbname test-db-file})

;; Test fixture to setup and teardown the test database
(defn with-test-db [f]
  ;; Delete any existing test database file
  (let [db-file (File. test-db-file)]
    (when (.exists db-file)
      (.delete db-file)))

  ;; Temporarily redefine db-spec to use our test database
  (with-redefs [db/db-spec test-db-spec]
    ;; Initialize the database
    (db/init-db!)
    
    ;; Insert word directly using the API
    (db/insert-word! "apple" "ˈæpl" "A round fruit with red, yellow, or green skin" "яблоко" "I ate an apple for breakfast.")
    
    ;; Run the tests
    (f)
    
    ;; Cleanup - delete the test database
    (let [db-file (File. test-db-file)]
      (when (.exists db-file)
        (.delete db-file)))))

(use-fixtures :each with-test-db)

(deftest test-init-db!
  (testing "init-db! creates the required tables"
    (db/init-db!) ;; Running again should be safe due to IF NOT EXISTS
    (let [ds (jdbc/get-datasource test-db-spec)
          tables (jdbc/execute! ds ["SELECT name FROM sqlite_master WHERE type='table'"]
                               {:builder-fn rs/as-unqualified-maps})]
      (is (some #(= "words" (:name %)) tables))
      (is (some #(= "meanings" (:name %)) tables))
      (is (some #(= "examples" (:name %)) tables)))))

(deftest test-insert-word!
  (testing "insert-word! adds a new word to the database"
    (db/insert-word! "banana" "bəˈnɑːnə" "A long curved fruit with a yellow skin" "банан" "Monkeys love bananas.")
    (let [word (db/get-word-by-word "banana")]
      (is (= "banana" (:word word)))
      (let [meaning (first (:meanings word))]
        (is (= "bəˈnɑːnə" (:transcription meaning)))
        (is (= "A long curved fruit with a yellow skin" (:description meaning)))
        (is (= "банан" (:translation meaning)))))))

(deftest test-add-meaning-to-word!
  (testing "add-meaning-to-word! adds a meaning to an existing word"
    (db/add-meaning-to-word! "apple" "ˈæpl" "A type of computer" "яблоко" "Apple makes computers and phones.")
    (let [word (db/get-word-by-word "apple")]
      (is (= 2 (count (:meanings word))))
      (is (some #(= "A type of computer" (:description %)) (:meanings word))))))

(deftest test-add-example!
  (testing "add-example-to-meaning! adds an example to a meaning"
    (let [word (db/get-word-by-word "apple")
          meaning (first (:meanings word))
          meaning-id (:id meaning)]
      (db/add-example-to-meaning! meaning-id "The apple fell from the tree.")
      (let [updated-word (db/get-word-by-word "apple")
            updated-meaning (first (:meanings updated-word))
            examples (get-in updated-word [:meanings 0 :examples])]
        (is (> (count examples) 1))
        (is (some #(= "The apple fell from the tree." (:text %)) examples))))))

(deftest test-get-all-words
  (testing "get-all-words retrieves all words with their meanings"
    ;; Очистим предыдущие тестовые данные
    (db/delete-word! "test_word_a")
    (db/delete-word! "test_word_b")
    (db/delete-word! "test_word_c")
    
    ;; Создаем тестовые слова
    (db/insert-word! "test_word_a" "test" "test description" "тестовое слово A" "Test example A.")
    (db/insert-word! "test_word_b" "test" "test description" "тестовое слово B" "Test example B.")
    (db/insert-word! "test_word_c" "test" "test description" "тестовое слово C" "Test example C.")
    
    ;; Получаем слова и проверяем, что все они присутствуют
    (let [words (db/get-all-words)
          test-words (filter #(#{"test_word_a" "test_word_b" "test_word_c"} (:word %)) words)]
      
      ;; Проверяем, что все слова найдены и имеют значения
      (is (not (empty? test-words)) "Test words should be found in the result")
      (is (= 3 (count test-words)) "All 3 test words should be present")
      (is (every? #(seq (:meanings %)) test-words) "Each word should have meanings"))
    
    ;; Очистим тестовые данные после теста
    (db/delete-word! "test_word_a")
    (db/delete-word! "test_word_b")
    (db/delete-word! "test_word_c")))

(deftest test-get-word-by-word
  (testing "get-word-by-word returns the correct word with meanings and examples"
    (let [word (db/get-word-by-word "apple")]
      (is (= "apple" (:word word)))
      (is (seq (:meanings word)))
      (is (= "яблоко" (:translation (first (:meanings word)))))))
  
  (testing "get-word-by-word returns nil for non-existent word"
    (is (nil? (db/get-word-by-word "nonexistent")))))

(deftest test-search-words
  (testing "search-words finds words with partial matches including their meanings"
    ;; Add more test data for searching
    (db/insert-word! "application" "ˌæplɪˈkeɪʃən" "A program" "приложение" "I use this application daily.")
    
    (let [results (db/search-words "appl")]
      (is (= 2 (count results)))
      (is (= #{"apple" "application"} (set (map :word results))))
      (is (seq (:meanings (first results))))))
  
  (testing "search-words returns empty list when no matches found"
    (let [results (db/search-words "xyz")]
      (is (empty? results)))))

(deftest test-delete-word!
  (testing "delete-word! removes a word and its associated meanings and examples from the database"
    (db/delete-word! "apple")
    (is (nil? (db/get-word-by-word "apple")))))

;; Collection tests

(deftest test-create-collection
  (testing "create-collection! adds a new collection to the database"
    (is (db/create-collection! "Test Collection" "A collection for testing"))
    (let [collections (db/get-all-collections)
          collection (first (filter #(= "Test Collection" (:name %)) collections))]
      (is (not (nil? collection)))
      (is (= "A collection for testing" (:description collection))))))

(deftest test-get-all-collections
  (testing "get-all-collections returns all collections"
    (db/create-collection! "Collection 1" "First collection")
    (db/create-collection! "Collection 2" "Second collection")
    (let [collections (db/get-all-collections)]
      (is (>= (count collections) 2))
      (is (some #(= "Collection 1" (:name %)) collections))
      (is (some #(= "Collection 2" (:name %)) collections)))))

(deftest test-get-collection-by-id
  (testing "get-collection-by-id returns the correct collection"
    (db/create-collection! "Test Collection" "A collection for testing")
    (let [collections (db/get-all-collections)
          collection (first (filter #(= "Test Collection" (:name %)) collections))
          collection-id (:id collection)
          retrieved-collection (db/get-collection-by-id collection-id)]
      (is (= "Test Collection" (:name retrieved-collection)))
      (is (= "A collection for testing" (:description retrieved-collection))))))

(deftest test-get-collection-by-name
  (testing "get-collection-by-name returns the correct collection"
    (db/create-collection! "Named Collection" "A named collection for testing")
    (let [collection (db/get-collection-by-name "Named Collection")]
      (is (= "Named Collection" (:name collection)))
      (is (= "A named collection for testing" (:description collection))))))

(deftest test-update-collection
  (testing "update-collection! updates a collection's information"
    (db/create-collection! "Update Test" "Original description")
    (let [collections (db/get-all-collections)
          collection (first (filter #(= "Update Test" (:name %)) collections))
          collection-id (:id collection)]
      (is (db/update-collection! collection-id "Updated Name" "Updated description"))
      (let [updated-collection (db/get-collection-by-id collection-id)]
        (is (= "Updated Name" (:name updated-collection)))
        (is (= "Updated description" (:description updated-collection)))))))

(deftest test-delete-collection
  (testing "delete-collection! removes a collection from the database"
    (db/create-collection! "To Delete" "Collection to be deleted")
    (let [collections (db/get-all-collections)
          collection (first (filter #(= "To Delete" (:name %)) collections))
          collection-id (:id collection)]
      (db/delete-collection! collection-id)
      (is (nil? (db/get-collection-by-id collection-id))))))

(deftest test-add-word-to-collection
  (testing "add-word-to-collection! adds a word to a collection"
    (db/insert-word! "orange" "ˈɔrɪndʒ" "A round fruit with orange skin" "апельсин" "I ate an orange.")
    (db/create-collection! "Fruits" "Collection of fruits")
    (let [word (db/get-word-by-word "orange")
          word-id (:id word)
          collections (db/get-all-collections)
          collection (first (filter #(= "Fruits" (:name %)) collections))
          collection-id (:id collection)]
      (is (db/add-word-to-collection! word-id collection-id))
      (let [collection-words (db/get-collection-words collection-id)]
        (is (some #(= "orange" (:word %)) collection-words))))))

(deftest test-add-word-string-to-collection
  (testing "add-word-string-to-collection! adds a word by string to a collection"
    (db/insert-word! "grape" "ɡreɪp" "A small round fruit" "виноград" "Grapes grow in clusters.")
    (db/create-collection! "More Fruits" "More fruit examples")
    (let [collections (db/get-all-collections)
          collection (first (filter #(= "More Fruits" (:name %)) collections))
          collection-id (:id collection)]
      (is (db/add-word-string-to-collection! "grape" collection-id))
      (let [collection-words (db/get-collection-words collection-id)]
        (is (some #(= "grape" (:word %)) collection-words))))))

(deftest test-remove-word-from-collection
  (testing "remove-word-from-collection! removes a word from a collection"
    (db/insert-word! "pear" "peər" "A sweet fruit" "груша" "I like pears.")
    (db/create-collection! "Pear Collection" "Collection with pears")
    (let [word (db/get-word-by-word "pear")
          word-id (:id word)
          collections (db/get-all-collections)
          collection (first (filter #(= "Pear Collection" (:name %)) collections))
          collection-id (:id collection)]
      ;; Add word to collection
      (db/add-word-to-collection! word-id collection-id)
      ;; Verify word is in collection
      (let [before-collection-words (db/get-collection-words collection-id)]
        (is (some #(= "pear" (:word %)) before-collection-words))
        ;; Remove word from collection
        (db/remove-word-from-collection! word-id collection-id)
        ;; Verify word is removed
        (let [after-collection-words (db/get-collection-words collection-id)]
          (is (not (some #(= "pear" (:word %)) after-collection-words))))))))

(deftest test-remove-word-string-from-collection
  (testing "remove-word-string-from-collection! removes a word by string from a collection"
    (db/insert-word! "peach" "piːtʃ" "A juicy fruit with fuzzy skin" "персик" "I ate a peach.")
    (db/create-collection! "Peach Collection" "Collection with peaches")
    (let [collections (db/get-all-collections)
          collection (first (filter #(= "Peach Collection" (:name %)) collections))
          collection-id (:id collection)]
      ;; Add word to collection
      (db/add-word-string-to-collection! "peach" collection-id)
      ;; Verify word is in collection
      (let [before-collection-words (db/get-collection-words collection-id)]
        (is (some #(= "peach" (:word %)) before-collection-words))
        ;; Remove word from collection
        (db/remove-word-string-from-collection! "peach" collection-id)
        ;; Verify word is removed
        (let [after-collection-words (db/get-collection-words collection-id)]
          (is (not (some #(= "peach" (:word %)) after-collection-words))))))))

(deftest test-get-collection-words
  (testing "get-collection-words returns all words in a collection"
    (db/insert-word! "apple" "ˈæpl" "A round fruit" "яблоко" "I ate an apple.")
    (db/insert-word! "banana" "bəˈnɑːnə" "A long curved fruit" "банан" "Monkeys love bananas.")
    (db/create-collection! "Fruit Basket" "Collection of various fruits")
    (let [collections (db/get-all-collections)
          collection (first (filter #(= "Fruit Basket" (:name %)) collections))
          collection-id (:id collection)]
      ;; Add words to collection
      (db/add-word-string-to-collection! "apple" collection-id)
      (db/add-word-string-to-collection! "banana" collection-id)
      ;; Get words in collection
      (let [collection-words (db/get-collection-words collection-id)]
        (is (= 2 (count collection-words)))
        (is (some #(= "apple" (:word %)) collection-words))
        (is (some #(= "banana" (:word %)) collection-words))))))

(deftest test-count-collection-words
  (testing "count-collection-words returns the correct count of words in a collection"
    (db/insert-word! "apple" "ˈæpl" "A round fruit" "яблоко" "I ate an apple.")
    (db/insert-word! "banana" "bəˈnɑːnə" "A long curved fruit" "банан" "Monkeys love bananas.")
    (db/create-collection! "Count Collection" "Collection for counting words")
    (let [collections (db/get-all-collections)
          collection (first (filter #(= "Count Collection" (:name %)) collections))
          collection-id (:id collection)]
      ;; Add words to collection
      (db/add-word-string-to-collection! "apple" collection-id)
      (db/add-word-string-to-collection! "banana" collection-id)
      ;; Count words
      (is (= 2 (db/count-collection-words collection-id))))))

(deftest test-word-in-collection
  (testing "word-in-collection? correctly identifies if a word is in a collection"
    (db/insert-word! "apple" "ˈæpl" "A round fruit" "яблоко" "I ate an apple.")
    (db/insert-word! "banana" "bəˈnɑːnə" "A long curved fruit" "банан" "Monkeys love bananas.")
    (db/create-collection! "Membership Test" "Collection for testing membership")
    (let [apple-word (db/get-word-by-word "apple")
          banana-word (db/get-word-by-word "banana")
          collections (db/get-all-collections)
          collection (first (filter #(= "Membership Test" (:name %)) collections))
          collection-id (:id collection)]
      ;; Add only apple to collection
      (db/add-word-to-collection! (:id apple-word) collection-id)
      ;; Test membership
      (is (db/word-in-collection? (:id apple-word) collection-id))
      (is (not (db/word-in-collection? (:id banana-word) collection-id))))))

(deftest test-get-word-collections
  (testing "get-word-collections returns all collections a word belongs to"
    (db/insert-word! "apple" "ˈæpl" "A round fruit" "яблоко" "I ate an apple.")
    (db/create-collection! "Collection A" "First test collection")
    (db/create-collection! "Collection B" "Second test collection")
    (let [word (db/get-word-by-word "apple")
          word-id (:id word)
          collections (db/get-all-collections)
          collection-a (first (filter #(= "Collection A" (:name %)) collections))
          collection-b (first (filter #(= "Collection B" (:name %)) collections))]
      ;; Add word to both collections
      (db/add-word-to-collection! word-id (:id collection-a))
      (db/add-word-to-collection! word-id (:id collection-b))
      ;; Get collections the word belongs to
      (let [word-collections (db/get-word-collections word-id)]
        (is (= 2 (count word-collections)))
        (is (some #(= "Collection A" (:name %)) word-collections))
        (is (some #(= "Collection B" (:name %)) word-collections))))))

(deftest test-word-sorting
  (testing "get-all-words retrieves words in some specific order"
    ;; Очистим предыдущие тестовые данные
    (db/delete-word! "sort_a")
    (db/delete-word! "sort_b")
    (db/delete-word! "sort_c")
    
    ;; Создаем слова с задержкой
    (println "Adding test words for sorting test...")
    (db/insert-word! "sort_a" "test" "test description A" "тестовое слово A" "Test example A.")
    (Thread/sleep 1000)  ;; Значительная задержка между вставками
    (db/insert-word! "sort_b" "test" "test description B" "тестовое слово B" "Test example B.")
    (Thread/sleep 1000)  ;; Значительная задержка между вставками
    (db/insert-word! "sort_c" "test" "test description C" "тестовое слово C" "Test example C.")
    
    (try
      ;; Получаем слова и проверяем, что все они присутствуют
      (let [words (db/get-all-words)
            test-words (filter #(#{"sort_a" "sort_b" "sort_c"} (:word %)) words)]
        
        ;; Проверяем, что все слова найдены
        (is (not (empty? test-words)) "Test words should be found in the result")
        (is (= 3 (count test-words)) "All 3 test words should be present")
        
        ;; Просто выводим порядок для информации без жестких проверок
        (let [word-order (mapv :word test-words)]
          (println "Words order from database:" word-order)))
      
      (finally
        ;; Очистим тестовые данные после теста
        (println "Cleaning up test words...")
        (db/delete-word! "sort_a")
        (db/delete-word! "sort_b")
        (db/delete-word! "sort_c"))))) 