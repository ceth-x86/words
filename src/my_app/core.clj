(ns my-app.core
  (:require [my-app.db :as db]
            [my-app.import :as imp]
            [my-app.export :as exp]
            [my-app.api :as api]
            [ring.adapter.jetty :as jetty]
            [clojure.string :as str])
  (:gen-class))

(defn show-usage []
  (println "Использование:")
  (println "  lein run init                   - Инициализировать базу данных")
  (println "  lein run add \"word\" \"transcription\" \"description\" \"translation\" \"examples\" - Добавить новое слово")
  (println "  lein run show                   - Показать все слова")
  (println "  lein run search \"term\"          - Искать слова по запросу")
  (println "  lein run get \"word\"             - Показать конкретное слово")
  (println "  lein run update \"word\" \"transcription\" \"description\" \"translation\" \"examples\" - Обновить слово")
  (println "  lein run delete \"word\"          - Удалить слово")
  (println "  lein run import \"file_path\"     - Импортировать слова из файла")
  (println "  lein run export \"file_path\"     - Экспортировать все слова в файл")
  (println "  lein run export-search \"term\" \"file_path\" - Экспортировать результаты поиска в файл")
  (println "  lein run server [port]          - Запустить веб-сервер с REST API (по умолчанию порт 3000)"))

(defn init-database []
  (println "Инициализация базы данных английских слов...")
  (db/init-db!)
  (println "База данных успешно инициализирована."))

(defn add-word [word transcription description translation examples]
  (println (str "Добавление слова: \"" word "\""))
  (db/insert-word! word transcription description translation examples)
  (println "Слово успешно добавлено."))

(defn format-word [word]
  (println (str "Слово: " (:word word) 
                "\nТранскрипция: " (:transcription word) 
                "\nОписание: " (:description word)
                "\nПеревод: " (:translation word) 
                "\nПримеры: " (:examples word)
                "\n")))

(defn show-words []
  (println "Список всех английских слов:")
  (let [words (db/get-all-words)]
    (if (empty? words)
      (println "База данных пуста.")
      (doseq [word words]
        (format-word word)))))

(defn get-word [word-str]
  (println (str "Получение информации о слове: \"" word-str "\""))
  (if-let [word (db/get-word-by-word word-str)]
    (format-word word)
    (println (str "Слово \"" word-str "\" не найдено."))))

(defn update-word [word transcription description translation examples]
  (println (str "Обновление слова: \"" word "\""))
  (if (db/get-word-by-word word)
    (do
      (db/update-word! word transcription description translation examples)
      (println "Слово успешно обновлено."))
    (println (str "Слово \"" word "\" не найдено."))))

(defn delete-word [word]
  (println (str "Удаление слова: \"" word "\""))
  (if (db/get-word-by-word word)
    (do
      (db/delete-word! word)
      (println "Слово успешно удалено."))
    (println (str "Слово \"" word "\" не найдено."))))

(defn search-words [term]
  (println (str "Поиск слов по запросу: \"" term "\""))
  (let [results (db/search-words term)]
    (if (empty? results)
      (println "Ничего не найдено.")
      (do
        (println (str "Найдено " (count results) " слов:"))
        (doseq [word results]
          (format-word word))))))

(defn import-words [file-path]
  (println (str "Импорт слов из файла: " file-path))
  (let [count (imp/import-words-from-file file-path)]
    (println (str "Процесс импорта завершен. Успешно импортировано слов: " count))))

(defn export-words [file-path]
  (println (str "Экспорт всех слов в файл: " file-path))
  (let [count (exp/export-words-to-file file-path)]
    (println (str "Процесс экспорта завершен. Успешно экспортировано слов: " count))))

(defn export-search-results [term file-path]
  (println (str "Экспорт результатов поиска \"" term "\" в файл: " file-path))
  (let [count (exp/export-search-results-to-file term file-path)]
    (println (str "Процесс экспорта завершен. Успешно экспортировано слов: " count))))

(defn start-server [port]
  (let [port-num (if port (Integer/parseInt port) 3000)]
    (println (str "Запуск веб-сервера на порту " port-num "..."))
    (println "API доступно по адресу: http://localhost:" port-num "/api/")
    (println "Нажмите Ctrl+C для остановки сервера.")
    (db/init-db!) ; инициализируем БД при запуске сервера
    (jetty/run-jetty api/app {:port port-num :join? true})))

(defn -main
  "Application entry point"
  [& args]
  (db/init-db!) ; всегда инициализируем базу данных при запуске
  
  (cond
    (empty? args)
    (show-usage)
    
    (= (first args) "init")
    (init-database)
    
    (= (first args) "add")
    (if (>= (count args) 6)
      (add-word (nth args 1) (nth args 2) (nth args 3) (nth args 4) (nth args 5))
      (println "Ошибка: команда add требует слова, транскрипции, описания, перевода и примеров."))
    
    (= (first args) "show")
    (show-words)
    
    (= (first args) "get")
    (if (>= (count args) 2)
      (get-word (nth args 1))
      (println "Ошибка: команда get требует указания слова."))
    
    (= (first args) "update")
    (if (>= (count args) 6)
      (update-word (nth args 1) (nth args 2) (nth args 3) (nth args 4) (nth args 5))
      (println "Ошибка: команда update требует слова, транскрипции, описания, перевода и примеров."))
    
    (= (first args) "delete")
    (if (>= (count args) 2)
      (delete-word (nth args 1))
      (println "Ошибка: команда delete требует указания слова."))
    
    (= (first args) "search")
    (if (>= (count args) 2)
      (search-words (nth args 1))
      (println "Ошибка: команда search требует поискового запроса."))
    
    (= (first args) "import")
    (if (>= (count args) 2)
      (import-words (nth args 1))
      (println "Ошибка: команда import требует пути к файлу."))
    
    (= (first args) "export")
    (if (>= (count args) 2)
      (export-words (nth args 1))
      (println "Ошибка: команда export требует пути к файлу."))

    (= (first args) "export-search")
    (if (>= (count args) 3)
      (export-search-results (nth args 1) (nth args 2))
      (println "Ошибка: команда export-search требует поискового запроса и пути к файлу."))
    
    (= (first args) "server")
    (start-server (second args))
    
    :else
    (do
      (println "Неизвестная команда.")
      (show-usage))))
