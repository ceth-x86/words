(ns my-app.api
  (:require [compojure.core :refer [defroutes GET POST PUT DELETE context]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.util.response :refer [response not-found created]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [my-app.db :as db]
            [my-app.import :as imp]
            [my-app.export :as exp]))

;; Вспомогательные функции для обработки API запросов

(defn handle-error [e]
  (let [message (.getMessage e)]
    {:status 500
     :body {:error message}}))

(defn handle-not-found [message]
  {:status 404
   :body {:error message}})

(defn handle-success [data]
  {:status 200
   :body data})

;; Обработчики API для операций с базой данных

(defn api-init-db []
  (try
    (db/init-db!)
    (handle-success {:message "База данных успешно инициализирована"})
    (catch Exception e
      (handle-error e))))

(defn api-get-all-words []
  (try
    (let [words (db/get-all-words)]
      (handle-success {:words words
                       :count (count words)}))
    (catch Exception e
      (handle-error e))))

(defn api-get-word [word-str]
  (try
    (if-let [word (db/get-word-by-word word-str)]
      (handle-success {:word word})
      (handle-not-found (str "Слово \"" word-str "\" не найдено")))
    (catch Exception e
      (handle-error e))))

(defn api-search-words [term]
  (try
    (let [results (db/search-words term)]
      (handle-success {:words results
                       :count (count results)}))
    (catch Exception e
      (handle-error e))))

(defn api-add-word [params]
  (try
    (let [{:keys [word transcription description translation examples]} params]
      (if (and word translation)
        (do
          (db/insert-word! word transcription description translation examples)
          (if-let [added-word (db/get-word-by-word word)]
            (handle-success {:message (str "Слово \"" word "\" успешно добавлено")
                             :word added-word})
            (handle-error (Exception. (str "Не удалось получить добавленное слово \"" word "\"")))))
        (handle-error (Exception. "Обязательные поля: word, translation"))))
    (catch Exception e
      (handle-error e))))

(defn api-update-word [word-str params]
  (try
    (let [{:keys [transcription description translation examples]} params]
      (if (db/get-word-by-word word-str)
        (do 
          (db/update-word! word-str transcription description translation examples)
          (if-let [updated-word (db/get-word-by-word word-str)]
            (handle-success {:message (str "Слово \"" word-str "\" успешно обновлено")
                             :word updated-word})
            (handle-error (Exception. (str "Не удалось получить обновленное слово \"" word-str "\"")))))
        (handle-not-found (str "Слово \"" word-str "\" не найдено"))))
    (catch Exception e
      (handle-error e))))

(defn api-delete-word [word-str]
  (try
    (if (db/get-word-by-word word-str)
      (do
        (db/delete-word! word-str)
        (handle-success {:message (str "Слово \"" word-str "\" успешно удалено")}))
      (handle-not-found (str "Слово \"" word-str "\" не найдено")))
    (catch Exception e
      (handle-error e))))

;; Обработчики API для операций импорта/экспорта

(defn read-file-content [file]
  (try
    (slurp (:tempfile file))
    (catch Exception e
      (throw (Exception. (str "Не удалось прочитать файл: " (.getMessage e)))))))

(defn api-import-words [params]
  (try
    (if-let [file (:file params)]
      (let [content (read-file-content file)
            words-data (imp/parse-words-file content)
            count (count words-data)]
        (db/batch-import-words! words-data)
        (handle-success {:message (str "Успешно импортировано слов: " count)
                         :count count}))
      (handle-error (Exception. "Файл для импорта не предоставлен")))
    (catch Exception e
      (handle-error e))))

(defn api-export-words []
  (try
    (let [words (db/get-all-words)
          content (exp/format-words-for-export words)]
      {:status 200
       :headers {"Content-Type" "text/plain; charset=utf-8"
                 "Content-Disposition" "attachment; filename=\"words_export.txt\""}
       :body content})
    (catch Exception e
      (handle-error e))))

(defn api-export-search-results [term]
  (try
    (let [words (db/search-words term)
          content (exp/format-words-for-export words)]
      {:status 200
       :headers {"Content-Type" "text/plain; charset=utf-8"
                 "Content-Disposition" (str "attachment; filename=\"search_" term "_export.txt\"")}
       :body content})
    (catch Exception e
      (handle-error e))))

;; Маршруты API

(defroutes app-routes
  ;; Базовые операции
  (GET "/api/words" [] (api-get-all-words))
  (GET "/api/words/:word" [word] (api-get-word word))
  (GET "/api/search" [term] (api-search-words term))
  (POST "/api/words" {body :body} (api-add-word body))
  (PUT "/api/words/:word" [word :as {body :body}] (api-update-word word body))
  (DELETE "/api/words/:word" [word] (api-delete-word word))
  
  ;; Инициализация БД
  (POST "/api/init" [] (api-init-db))
  
  ;; Импорт/Экспорт
  (POST "/api/import" {params :params} (api-import-words params))
  (GET "/api/export" [] (api-export-words))
  (GET "/api/export/search" [term] (api-export-search-results term))
  
  ;; Обработка неизвестных маршрутов
  (route/not-found {:error "Маршрут не найден"}))

;; Middleware обертки

(def app
  (-> app-routes
      wrap-keyword-params
      wrap-params
      wrap-multipart-params
      (wrap-json-body {:keywords? true})
      wrap-json-response)) 