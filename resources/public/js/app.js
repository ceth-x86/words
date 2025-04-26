const API_BASE_URL = '/api';

const app = Vue.createApp({
    setup() {
        const { ref, reactive, computed, watch, onMounted } = Vue;
        
        // Основные состояния
        const title = ref('English Words Dictionary');
        const words = ref([]);
        const searchTerm = ref('');
        const searched = ref(false);
        const stats = ref(null);
        
        // Навигация по вкладкам
        const activeTab = ref('words');
        
        // Данные вкладки слов
        const showAddWordForm = ref(false);
        const showAddMeaningForm = reactive({});
        const selectedWordId = ref(null);
        const newWord = reactive({
            word: '',
            transcription: '',
            description: '',
            translation: '',
            examples: ''
        });
        const newMeaning = reactive({
            transcription: '',
            description: '',
            translation: '',
            examples: ''
        });
        const newExamples = reactive({});
        
        // Данные вкладки коллекций
        const collections = ref([]);
        const showAddCollectionForm = ref(false);
        const newCollection = reactive({
            name: '',
            description: ''
        });
        const activeCollection = ref(null);
        const collectionWords = ref([]);
        const wordToAdd = ref('');
        
        // Фильтр для слов по коллекции
        const filteredByCollection = ref(null);
        
        // Модальное окно редактирования коллекции
        const showEditCollection = ref(false);
        const editCollection = reactive({
            id: null,
            name: '',
            description: ''
        });
        
        // Модальное окно коллекций слова
        const showWordCollectionsModal = ref(false);
        const currentWord = ref('');
        const wordCollections = ref([]);
        const availableCollections = ref([]);
        const selectedCollection = ref('');
        
        // Уведомления
        const notification = reactive({
            show: false,
            message: '',
            type: 'success', // success, error, warning
            timeout: null
        });
        
        // Вычисляемые свойства
        const filteredWords = computed(() => {
            return words.value;
        });
        
        // API запросы для слов
        function getAllWords() {
            searchTerm.value = '';
            searched.value = false;
            filteredByCollection.value = null; // Очистка фильтра коллекции
            
            axios.get(`${API_BASE_URL}/words`)
                .then(response => {
                    words.value = response.data.words;
                    stats.value = {
                        count: response.data.count,
                        total_meanings: response.data.total_meanings,
                        total_examples: response.data.total_examples
                    };
                })
                .catch(error => {
                    showNotification(`Error loading words: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function searchWords() {
            if (!searchTerm.value.trim()) {
                getAllWords();
                return;
            }
            
            searched.value = true;
            filteredByCollection.value = null; // Очистка фильтра коллекции
            
            axios.get(`${API_BASE_URL}/search?term=${encodeURIComponent(searchTerm.value)}`)
                .then(response => {
                    words.value = response.data.words;
                    stats.value = { count: response.data.count };
                })
                .catch(error => {
                    showNotification(`Search error: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function addWord() {
            if (!newWord.word || !newWord.translation) {
                showNotification('Word and translation are required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/words`, newWord)
                .then(response => {
                    showNotification(`Word "${newWord.word}" added successfully`, 'success');
                    // Очистка формы
                    resetNewWord();
                    showAddWordForm.value = false;
                    // Обновление списка слов
                    getAllWords();
                })
                .catch(error => {
                    showNotification(`Error adding word: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function addMeaning(wordStr) {
            if (!newMeaning.translation) {
                showNotification('Translation is required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}/meanings`, newMeaning)
                .then(response => {
                    showNotification(`New meaning added to "${wordStr}"`, 'success');
                    // Очистка формы
                    resetNewMeaning();
                    showAddMeaningForm[response.data.word.id] = false;
                    // Обновление списка слов
                    getAllWords();
                })
                .catch(error => {
                    showNotification(`Error adding meaning: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function addExample(meaningId) {
            const exampleText = newExamples[meaningId];
            if (!exampleText || !exampleText.trim()) {
                showNotification('Example text cannot be empty', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/meanings/${meaningId}/examples`, { text: exampleText })
                .then(response => {
                    showNotification('Example added successfully', 'success');
                    // Очистка поля ввода
                    newExamples[meaningId] = '';
                    // Обновление списка слов
                    getAllWords();
                })
                .catch(error => {
                    showNotification(`Error adding example: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function deleteWord(wordStr) {
            if (!confirm(`Are you sure you want to delete "${wordStr}" and all its meanings?`)) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}`)
                .then(response => {
                    showNotification(`Word "${wordStr}" deleted successfully`, 'success');
                    // Обновление списка слов
                    getAllWords();
                })
                .catch(error => {
                    showNotification(`Error deleting word: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function deleteMeaning(meaningId) {
            if (!confirm('Are you sure you want to delete this meaning?')) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/meanings/${meaningId}`)
                .then(response => {
                    showNotification('Meaning deleted successfully', 'success');
                    // Обновление списка слов
                    getAllWords();
                })
                .catch(error => {
                    showNotification(`Error deleting meaning: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function deleteExample(exampleId) {
            axios.delete(`${API_BASE_URL}/examples/${exampleId}`)
                .then(response => {
                    showNotification('Example deleted successfully', 'success');
                    // Обновление списка слов
                    getAllWords();
                })
                .catch(error => {
                    showNotification(`Error deleting example: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        // API запросы для коллекций
        function getAllCollections() {
            axios.get(`${API_BASE_URL}/collections`)
                .then(response => {
                    collections.value = response.data.collections;
                })
                .catch(error => {
                    showNotification(`Error loading collections: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function addCollection() {
            if (!newCollection.name) {
                showNotification('Collection name is required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/collections`, newCollection)
                .then(response => {
                    showNotification(`Collection "${newCollection.name}" created successfully`, 'success');
                    // Очистка формы
                    resetNewCollection();
                    showAddCollectionForm.value = false;
                    // Обновление коллекций
                    getAllCollections();
                })
                .catch(error => {
                    showNotification(`Error creating collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function showCollectionDetails(collectionId) {
            axios.get(`${API_BASE_URL}/collections/${collectionId}`)
                .then(response => {
                    activeCollection.value = response.data.collection;
                    collectionWords.value = response.data.words;
                })
                .catch(error => {
                    showNotification(`Error loading collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function closeCollectionDetails() {
            activeCollection.value = null;
            collectionWords.value = [];
            wordToAdd.value = '';
        }
        
        function addWordToCollection() {
            if (!wordToAdd.value.trim()) {
                showNotification('Word to add is required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/collections/${activeCollection.value.id}/words/${encodeURIComponent(wordToAdd.value)}`)
                .then(response => {
                    showNotification(`Word "${wordToAdd.value}" added to collection`, 'success');
                    wordToAdd.value = '';
                    // Обновление деталей коллекции
                    showCollectionDetails(activeCollection.value.id);
                })
                .catch(error => {
                    showNotification(`Error adding word to collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function removeWordFromCollection(wordStr) {
            if (!confirm(`Are you sure you want to remove "${wordStr}" from this collection?`)) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/collections/${activeCollection.value.id}/words/${encodeURIComponent(wordStr)}`)
                .then(response => {
                    showNotification(`Word "${wordStr}" removed from collection`, 'success');
                    // Обновление деталей коллекции
                    showCollectionDetails(activeCollection.value.id);
                })
                .catch(error => {
                    showNotification(`Error removing word from collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function showEditCollectionForm(collection) {
            editCollection.id = collection.id;
            editCollection.name = collection.name;
            editCollection.description = collection.description || '';
            showEditCollection.value = true;
        }
        
        function closeEditCollection() {
            showEditCollection.value = false;
            editCollection.id = null;
            editCollection.name = '';
            editCollection.description = '';
        }
        
        function updateCollection() {
            if (!editCollection.name) {
                showNotification('Collection name is required', 'warning');
                return;
            }
            
            axios.put(`${API_BASE_URL}/collections/${editCollection.id}`, {
                name: editCollection.name,
                description: editCollection.description
            })
                .then(response => {
                    showNotification('Collection updated successfully', 'success');
                    closeEditCollection();
                    // Обновление коллекций
                    getAllCollections();
                })
                .catch(error => {
                    showNotification(`Error updating collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function deleteCollection(collectionId) {
            if (!confirm('Are you sure you want to delete this collection?')) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/collections/${collectionId}`)
                .then(response => {
                    showNotification('Collection deleted successfully', 'success');
                    // Обновление коллекций
                    getAllCollections();
                })
                .catch(error => {
                    showNotification(`Error deleting collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        // Интеграция слов и коллекций
        function showWordCollections(wordStr) {
            currentWord.value = wordStr;
            showWordCollectionsModal.value = true;
            
            // Получение коллекций для этого слова
            axios.get(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}/collections`)
                .then(response => {
                    wordCollections.value = response.data.collections;
                    // Получение всех коллекций для определения доступных
                    return axios.get(`${API_BASE_URL}/collections`);
                })
                .then(response => {
                    const allCollections = response.data.collections;
                    const wordCollectionIds = wordCollections.value.map(c => c.id);
                    availableCollections.value = allCollections.filter(
                        c => !wordCollectionIds.includes(c.id)
                    );
                })
                .catch(error => {
                    showNotification(`Error loading collections: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function closeWordCollections() {
            showWordCollectionsModal.value = false;
            currentWord.value = '';
            wordCollections.value = [];
            availableCollections.value = [];
            selectedCollection.value = '';
        }
        
        function addToSelectedCollection() {
            if (!selectedCollection.value) {
                showNotification('Please select a collection', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/collections/${selectedCollection.value}/words/${encodeURIComponent(currentWord.value)}`)
                .then(response => {
                    showNotification(`Word added to collection successfully`, 'success');
                    // Обновление коллекций для этого слова
                    showWordCollections(currentWord.value);
                })
                .catch(error => {
                    showNotification(`Error adding to collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function removeFromCollection(collectionId) {
            axios.delete(`${API_BASE_URL}/collections/${collectionId}/words/${encodeURIComponent(currentWord.value)}`)
                .then(response => {
                    showNotification(`Word removed from collection successfully`, 'success');
                    // Обновление коллекций для этого слова
                    showWordCollections(currentWord.value);
                })
                .catch(error => {
                    showNotification(`Error removing from collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function viewCollectionWords(collection) {
            // Получение слов из коллекции
            axios.get(`${API_BASE_URL}/collections/${collection.id}`)
                .then(response => {
                    // Переключение на вкладку слов
                    activeTab.value = 'words';
                    
                    // Сохранение деталей коллекции для фильтрации
                    filteredByCollection.value = {
                        id: collection.id,
                        name: collection.name
                    };
                    
                    // Получение полной информации о каждом слове в коллекции
                    const wordsFromCollection = response.data.words;
                    if (wordsFromCollection && wordsFromCollection.length > 0) {
                        // Получение полной информации для каждого слова
                        const wordPromises = wordsFromCollection.map(word => 
                            axios.get(`${API_BASE_URL}/words/${encodeURIComponent(word.word)}`)
                                .then(resp => resp.data.word)
                        );
                        
                        Promise.all(wordPromises)
                            .then(wordDetails => {
                                words.value = wordDetails;
                                
                                // Обновление статистики
                                stats.value = {
                                    count: words.value.length,
                                    filtered_by: collection.name
                                };
                                
                                // Отметка для отображения соответствующего сообщения
                                searchTerm.value = '';
                                searched.value = true;
                            })
                            .catch(error => {
                                showNotification(`Error loading word details: ${getErrorMessage(error)}`, 'error');
                            });
                    } else {
                        // Нет слов в коллекции
                        words.value = [];
                        stats.value = {
                            count: 0,
                            filtered_by: collection.name
                        };
                        searchTerm.value = '';
                        searched.value = true;
                    }
                })
                .catch(error => {
                    showNotification(`Error loading collection words: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        // Вспомогательные функции для UI
        function resetNewWord() {
            Object.assign(newWord, {
                word: '',
                transcription: '',
                description: '',
                translation: '',
                examples: ''
            });
        }
        
        function resetNewMeaning() {
            Object.assign(newMeaning, {
                transcription: '',
                description: '',
                translation: '',
                examples: ''
            });
        }
        
        function resetNewCollection() {
            Object.assign(newCollection, {
                name: '',
                description: ''
            });
        }
        
        function showNotification(message, type = 'success') {
            // Очистка существующего таймаута
            if (notification.timeout) {
                clearTimeout(notification.timeout);
            }
            
            // Установка данных уведомления
            notification.message = message;
            notification.type = type;
            notification.show = true;
            
            // Установка таймаута для скрытия уведомления
            notification.timeout = setTimeout(() => {
                notification.show = false;
            }, 3000);
        }
        
        function getErrorMessage(error) {
            if (error.response && error.response.data && error.response.data.error) {
                return error.response.data.error;
            }
            return error.message || 'Unknown error';
        }
        
        function toggleWordDetails(word) {
            if (selectedWordId.value === word.id) {
                selectedWordId.value = null;
            } else {
                selectedWordId.value = word.id;
            }
        }
        
        // Отслеживание изменений
        watch(activeTab, (newTab) => {
            if (newTab === 'words') {
                getAllWords();
            } else if (newTab === 'collections') {
                getAllCollections();
            }
        });
        
        // Хуки жизненного цикла
        onMounted(() => {
            // Загрузка слов при монтировании компонента
            getAllWords();
            
            // Загрузка коллекций
            getAllCollections();
        });
        
        // Возвращаем все необходимые данные и методы для использования в шаблоне
        return {
            // Состояния
            title,
            words,
            searchTerm,
            searched,
            stats,
            activeTab,
            showAddWordForm,
            showAddMeaningForm,
            selectedWordId,
            newWord,
            newMeaning,
            newExamples,
            collections,
            showAddCollectionForm,
            newCollection,
            activeCollection,
            collectionWords,
            wordToAdd,
            filteredByCollection,
            showEditCollection,
            editCollection,
            showWordCollectionsModal,
            currentWord,
            wordCollections,
            availableCollections,
            selectedCollection,
            notification,
            
            // Методы
            getAllWords,
            searchWords,
            addWord,
            addMeaning,
            addExample,
            deleteWord,
            deleteMeaning,
            deleteExample,
            getAllCollections,
            addCollection,
            showCollectionDetails,
            closeCollectionDetails,
            addWordToCollection,
            removeWordFromCollection,
            showEditCollectionForm,
            closeEditCollection,
            updateCollection,
            deleteCollection,
            showWordCollections,
            closeWordCollections,
            addToSelectedCollection,
            removeFromCollection,
            viewCollectionWords,
            toggleWordDetails,
            
            // Вычисляемые свойства
            filteredWords
        };
    }
});

app.mount('#app'); 