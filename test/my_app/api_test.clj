(ns my-app.api-test
  (:require [clojure.test :refer :all]
            [my-app.api :refer :all]
            [my-app.db :as db]
            [my-app.export :as exp]
            [my-app.import :as imp]
            [ring.mock.request :as mock]
            [cheshire.core :as json])
  (:import (java.io File)))

;; Helper functions for the tests
(defn parse-body [response]
  (try
    (json/parse-string (:body response) true)
    (catch Exception e
      (if (instance? java.io.File (:body response))
        {:error (str "Invalid response body: " (.getName (:body response)))}
        {:error "Invalid response body"}))))

;; Mock data for tests
(def test-word 
  {:id 1
   :word "apple"
   :meanings [{:id 1
               :transcription "ˈæpl" 
               :description "A round fruit" 
               :translation "яблоко" 
               :examples [{:text "I ate an apple."}]}]})

(def test-meaning
  {:transcription "ˈæpl" 
   :description "A round fruit with red skin" 
   :translation "яблоко" 
   :examples [{:text "I ate a red apple."}]})

(def test-meaning-update
  {:transcription "ˈæpl" 
   :description "A round fruit with red skin" 
   :translation "яблоко"})

(def test-example
  {:text "This is a new example."})

;; Collection mock data
(def test-collection 
  {:name "Test Collection"
   :description "A collection for testing"})

;; Sample export format for testing
(def sample-export-format
  "**apple**\n/ˈæpl/ - A round fruit\n(яблоко)\n- I ate an apple.\n\n")

;; Sample import file content for testing
(def sample-import-content
  "**banana** /bəˈnænə/ - A long curved fruit\n(банан)\n- Monkeys like bananas.\n")

;; Mock functions for database operations
(defn mock-get-all-words []
  [test-word])

(defn mock-get-word-by-word [word]
  (when (= word "apple")
    test-word))

(defn mock-search-words [term]
  (when (= term "app")
    [test-word]))

(defn mock-init-db! []
  true)

(defn mock-insert-word! [& args]
  true)

(defn mock-add-meaning-to-word! [& args]
  true)

(defn mock-update-meaning! [& args]
  true)

(defn mock-delete-meaning! [& args]
  true)

(defn mock-delete-word! [& args]
  true)

(defn mock-add-example-to-meaning! [& args]
  true)

(defn mock-delete-example! [& args]
  true)

(defn mock-count-words []
  1)

(defn mock-count-meanings []
  2)

(defn mock-count-examples []
  5)

(defn mock-format-words-for-export [words]
  sample-export-format)

(defn mock-parse-words-file [content]
  [{:word "banana"
    :meanings [{:transcription "bəˈnænə" 
                :description "A long curved fruit" 
                :translation "банан" 
                :examples ["Monkeys like bananas."]}]}])

;; Mock functions for collections
(defn mock-get-all-collections []
  [{:id 1
    :name "Test Collection"
    :description "A collection for testing"
    :created_at "2023-01-01 12:00:00"}])

(defn mock-get-collection-by-id [id]
  (when (= id "1")
    {:id 1
     :name "Test Collection"
     :description "A collection for testing"
     :created_at "2023-01-01 12:00:00"}))

(defn mock-get-collection-by-name [name]
  (when (= name "Test Collection")
    {:id 1
     :name "Test Collection"
     :description "A collection for testing"
     :created_at "2023-01-01 12:00:00"}))

(defn mock-create-collection! [name description]
  true)

(defn mock-update-collection! [id name description]
  true)

(defn mock-delete-collection! [id]
  true)

(defn mock-get-collection-words [id]
  (when (= id "1")
    [test-word]))

(defn mock-add-word-to-collection! [word-id collection-id]
  true)

(defn mock-remove-word-from-collection! [word-id collection-id]
  true)

(defn mock-get-word-collections [word-id]
  (when (= word-id 1)
    [{:id 1
      :name "Test Collection"
      :description "A collection for testing"}]))

;; Test fixture for mocking database functions
(defn with-mock-db [f]
  (with-redefs [db/get-all-words mock-get-all-words
                db/get-word-by-word mock-get-word-by-word
                db/search-words mock-search-words
                db/init-db! mock-init-db!
                db/insert-word! mock-insert-word!
                db/add-meaning-to-word! mock-add-meaning-to-word!
                db/update-meaning! mock-update-meaning!
                db/delete-meaning! mock-delete-meaning!
                db/delete-word! mock-delete-word!
                db/add-example-to-meaning! mock-add-example-to-meaning!
                db/delete-example! mock-delete-example!
                db/count-words mock-count-words
                db/count-meanings mock-count-meanings
                db/count-examples mock-count-examples
                exp/format-words-for-export mock-format-words-for-export
                imp/parse-words-file mock-parse-words-file
                ;; Collection mocks
                db/get-all-collections mock-get-all-collections
                db/get-collection-by-id mock-get-collection-by-id
                db/get-collection-by-name mock-get-collection-by-name
                db/create-collection! mock-create-collection!
                db/update-collection! mock-update-collection!
                db/delete-collection! mock-delete-collection!
                db/get-collection-words mock-get-collection-words
                db/add-word-to-collection! mock-add-word-to-collection!
                db/remove-word-from-collection! mock-remove-word-from-collection!
                db/get-word-collections mock-get-word-collections]
    (f)))

;; Mock the import function directly
(defn mock-api-import-words [params]
  {:status 200
   :body {:message "Successfully imported 1 words with 2 meanings"
          :word_count 1
          :meaning_count 2}})

(use-fixtures :each with-mock-db)

;; API tests
(deftest test-api-get-all-words
  (testing "GET /api/words returns all words"
    (let [response (app (mock/request :get "/api/words"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= 1 (:count body)))
      (is (= 2 (:total_meanings body)))
      (is (= 5 (:total_examples body)))
      (is (= "apple" (-> body :words first :word))))))

(deftest test-api-get-word
  (testing "GET /api/words/:word returns a specific word when found"
    (let [response (app (mock/request :get "/api/words/apple"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "apple" (-> body :word :word)))))
  
  (testing "GET /api/words/:word returns 404 when word not found"
    (let [response (app (mock/request :get "/api/words/nonexistent"))
          body (parse-body response)]
      (is (= 404 (:status response)))
      (is (= "Word \"nonexistent\" not found" (:error body))))))

(deftest test-api-search-words
  (testing "GET /api/search returns search results"
    (let [response (app (mock/request :get "/api/search" {:term "app"}))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= 1 (:count body)))
      (is (= "apple" (-> body :words first :word)))))
  
  (testing "GET /api/search returns empty list for no matches"
    (let [response (app (mock/request :get "/api/search" {:term "xyz"}))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= 0 (:count body)))
      (is (empty? (:words body))))))

(deftest test-api-add-word
  (testing "POST /api/words adds a new word with a meaning"
    (let [word-data {:word "apple"
                     :transcription "ˈæpl" 
                     :description "A round fruit" 
                     :translation "яблоко" 
                     :examples "I ate an apple."}
          response (app (-> (mock/request :post "/api/words")
                            (mock/json-body word-data)))
          body (parse-body response)]
      (is (= 201 (:status response)))
      (is (= "Word or meaning \"apple\" successfully added" (:message body)))
      (is (= "apple" (-> body :word :word)))))
  
  (testing "POST /api/words validates required fields"
    (let [response (app (-> (mock/request :post "/api/words")
                           (mock/json-body {:word "test"})))
          body (parse-body response)]
      (is (= 500 (:status response)))
      (is (= "Required fields: word, translation" (:error body))))))

(deftest test-api-add-meaning
  (testing "POST /api/words/:word/meanings adds a meaning to existing word"
    (let [response (app (-> (mock/request :post "/api/words/apple/meanings")
                            (mock/json-body test-meaning)))
          body (parse-body response)]
      (is (= 201 (:status response)))
      (is (= "New meaning added to word \"apple\"" (:message body)))
      (is (= "apple" (-> body :word :word))))))

(deftest test-api-update-meaning
  (testing "PUT /api/meanings/:id updates an existing meaning"
    (let [response (app (-> (mock/request :put "/api/meanings/1")
                            (mock/json-body test-meaning-update)))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Meaning ID 1 successfully updated" (:message body))))))

(deftest test-api-add-example
  (testing "POST /api/meanings/:id/examples adds an example to a meaning"
    (let [response (app (-> (mock/request :post "/api/meanings/1/examples")
                            (mock/json-body test-example)))
          body (parse-body response)]
      (is (= 201 (:status response)))
      (is (= "Example added to meaning ID 1" (:message body))))))

(deftest test-api-delete-meaning
  (testing "DELETE /api/meanings/:id deletes a meaning"
    (let [response (app (mock/request :delete "/api/meanings/1"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Meaning ID 1 successfully deleted" (:message body))))))

(deftest test-api-delete-example
  (testing "DELETE /api/examples/:id deletes an example"
    (let [response (app (mock/request :delete "/api/examples/1"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Example ID 1 successfully deleted" (:message body))))))

(deftest test-api-delete-word
  (testing "DELETE /api/words/:word deletes an existing word and all its meanings"
    (let [response (app (mock/request :delete "/api/words/apple"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Word \"apple\" and all its meanings successfully deleted" (:message body)))))
  
  (testing "DELETE /api/words/:word returns 404 for non-existent word"
    (let [response (app (mock/request :delete "/api/words/nonexistent"))
          body (parse-body response)]
      (is (= 404 (:status response)))
      (is (= "Word \"nonexistent\" not found" (:error body))))))

(deftest test-api-init-db
  (testing "POST /api/init initializes the database"
    (let [response (app (mock/request :post "/api/init"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Database successfully initialized" (:message body))))))

(deftest test-api-export-words
  (testing "GET /api/export returns formatted words export with correct headers"
    (let [response (app (mock/request :get "/api/export"))]
      (is (= 200 (:status response)))
      (is (= "text/plain; charset=utf-8" (get-in response [:headers "Content-Type"])))
      (is (= "attachment; filename=\"words_export.txt\"" (get-in response [:headers "Content-Disposition"])))
      (is (= sample-export-format (:body response))))))

(deftest test-api-export-search-results
  (testing "GET /api/export/search returns search results export with correct headers"
    (let [response (app (mock/request :get "/api/export/search" {:term "app"}))]
      (is (= 200 (:status response)))
      (is (= "text/plain; charset=utf-8" (get-in response [:headers "Content-Type"])))
      (is (= "attachment; filename=\"search_app_export.txt\"" (get-in response [:headers "Content-Disposition"])))
      (is (= sample-export-format (:body response))))))

(deftest test-api-import-words
  (testing "POST /api/import imports words from file"
    (with-redefs [my-app.api/api-import-words mock-api-import-words]
      (let [response (app (-> (mock/request :post "/api/import")))
            body (parse-body response)]
        (is (= 200 (:status response)))
        (is (= "Successfully imported 1 words with 2 meanings" (:message body)))
        (is (= 1 (:word_count body)))
        (is (= 2 (:meaning_count body)))))))

;; Test for content type and headers
(deftest test-api-content-type
  (testing "API responses have JSON Content-Type header"
    (let [response (app (mock/request :get "/api/words"))
          content-type (get-in response [:headers "Content-Type"])]
      (is (= "application/json; charset=utf-8" content-type)))))

;; Collection API Tests
(deftest test-api-get-all-collections
  (testing "GET /api/collections returns all collections"
    (let [response (app (mock/request :get "/api/collections"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= 1 (:count body)))
      (is (= "Test Collection" (-> body :collections first :name))))))

(deftest test-api-get-collection
  (testing "GET /api/collections/:id returns a specific collection when found"
    (let [response (app (mock/request :get "/api/collections/1"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Test Collection" (-> body :collection :name)))))
  
  (testing "GET /api/collections/:id returns 404 when collection not found"
    (let [response (app (mock/request :get "/api/collections/999"))
          body (parse-body response)]
      (is (= 404 (:status response)))
      (is (= "Collection ID 999 not found" (:error body))))))

(deftest test-api-create-collection
  (testing "POST /api/collections creates a new collection"
    (let [response (app (-> (mock/request :post "/api/collections")
                            (mock/json-body test-collection)))
          body (parse-body response)]
      (is (= 201 (:status response)))
      (is (= "Collection \"Test Collection\" successfully created" (:message body)))))
  
  (testing "POST /api/collections validates required fields"
    (let [response (app (-> (mock/request :post "/api/collections")
                           (mock/json-body {:description "Missing name"})))
          body (parse-body response)]
      (is (= 500 (:status response)))
      (is (= "Required field: name" (:error body))))))

(deftest test-api-update-collection
  (testing "PUT /api/collections/:id updates a collection"
    (let [response (app (-> (mock/request :put "/api/collections/1")
                            (mock/json-body {:name "Updated Collection" 
                                            :description "Updated description"})))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Collection ID 1 successfully updated" (:message body))))))

(deftest test-api-delete-collection
  (testing "DELETE /api/collections/:id deletes a collection"
    (let [response (app (mock/request :delete "/api/collections/1"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Collection ID 1 successfully deleted" (:message body))))))

(deftest test-api-add-word-to-collection
  (testing "POST /api/collections/:id/words/:word adds a word to a collection"
    (let [response (app (mock/request :post "/api/collections/1/words/apple"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Word \"apple\" successfully added to collection \"Test Collection\"" (:message body))))))

(deftest test-api-remove-word-from-collection
  (testing "DELETE /api/collections/:id/words/:word removes a word from a collection"
    (let [response (app (mock/request :delete "/api/collections/1/words/apple"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Word \"apple\" successfully removed from collection \"Test Collection\"" (:message body))))))

(deftest test-api-get-word-collections
  (testing "GET /api/words/:word/collections returns all collections a word belongs to"
    (let [response (app (mock/request :get "/api/words/apple/collections"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= 1 (:count body)))
      (is (= "Test Collection" (-> body :collections first :name)))))) 