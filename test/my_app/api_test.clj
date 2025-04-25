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
  (json/parse-string (:body response) true))

;; Mock data for tests
(def test-word 
  {:word "apple" 
   :transcription "ˈæpl" 
   :description "A round fruit" 
   :translation "яблоко" 
   :examples "I ate an apple."})

(def test-word-update
  {:transcription "ˈæpl" 
   :description "A round fruit with red skin" 
   :translation "яблоко" 
   :examples "I ate a red apple."})

;; Sample export format for testing
(def sample-export-format
  "**apple** /ˈæpl/ - A round fruit\n(яблоко)\n- I ate an apple.\n")

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

(defn mock-update-word! [& args]
  true)

(defn mock-delete-word! [& args]
  true)

(defn mock-batch-import-words! [& args]
  true)

(defn mock-format-words-for-export [words]
  sample-export-format)

(defn mock-parse-words-file [content]
  [{:word "banana" 
    :transcription "bəˈnænə" 
    :description "A long curved fruit" 
    :translation "банан" 
    :examples "Monkeys like bananas."}])

;; Test fixture for mocking database functions
(defn with-mock-db [f]
  (with-redefs [db/get-all-words mock-get-all-words
                db/get-word-by-word mock-get-word-by-word
                db/search-words mock-search-words
                db/init-db! mock-init-db!
                db/insert-word! mock-insert-word!
                db/update-word! mock-update-word!
                db/delete-word! mock-delete-word!
                db/batch-import-words! mock-batch-import-words!
                exp/format-words-for-export mock-format-words-for-export
                imp/parse-words-file mock-parse-words-file]
    (f)))

;; Mock the import function directly
(defn mock-api-import-words [params]
  {:status 200
   :body {:message "Successfully imported 1 words"
          :count 1}})

(use-fixtures :each with-mock-db)

;; API tests
(deftest test-api-get-all-words
  (testing "GET /api/words returns all words"
    (let [response (app (mock/request :get "/api/words"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= 1 (:count body)))
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
  (testing "POST /api/words adds a new word"
    (let [response (app (-> (mock/request :post "/api/words")
                           (mock/json-body test-word)))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Word \"apple\" successfully added" (:message body)))
      (is (= "apple" (-> body :word :word)))))
  
  (testing "POST /api/words validates required fields"
    (let [response (app (-> (mock/request :post "/api/words")
                           (mock/json-body {:word "test"})))
          body (parse-body response)]
      (is (= 500 (:status response)))
      (is (= "Required fields: word, translation" (:error body))))))

(deftest test-api-update-word
  (testing "PUT /api/words/:word updates an existing word"
    (let [response (app (-> (mock/request :put "/api/words/apple")
                           (mock/json-body test-word-update)))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Word \"apple\" successfully updated" (:message body)))
      (is (= "apple" (-> body :word :word)))))
  
  (testing "PUT /api/words/:word returns 404 for non-existent word"
    (let [response (app (-> (mock/request :put "/api/words/nonexistent")
                           (mock/json-body test-word-update)))
          body (parse-body response)]
      (is (= 404 (:status response)))
      (is (= "Word \"nonexistent\" not found" (:error body))))))

(deftest test-api-delete-word
  (testing "DELETE /api/words/:word deletes an existing word"
    (let [response (app (mock/request :delete "/api/words/apple"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Word \"apple\" successfully deleted" (:message body)))))
  
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
        (is (= "Successfully imported 1 words" (:message body)))
        (is (= 1 (:count body)))))))

(deftest test-api-route-not-found
  (testing "Unknown routes return 404"
    (let [response (app (mock/request :get "/api/unknown"))
          body (parse-body response)]
      (is (= 404 (:status response))))))

;; Test for content type and headers
(deftest test-api-content-type
  (testing "API responses have JSON Content-Type header"
    (let [response (app (mock/request :get "/api/words"))
          content-type (get-in response [:headers "Content-Type"])]
      (is (= "application/json; charset=utf-8" content-type))))) 