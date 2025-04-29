const API_BASE_URL = '/api';

const app = Vue.createApp({
    setup() {
        const { ref, reactive, computed, watch, onMounted } = Vue;
        
        // Core states
        const title = ref('English Words Dictionary');
        const words = ref([]);
        const searchTerm = ref('');
        const searched = ref(false);
        const stats = ref(null);
        
        // Navigation between tabs
        const activeTab = ref('words');
        
        // Data for words tab
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
        
        // Data for collections tab
        const collections = ref([]);
        const showAddCollectionForm = ref(false);
        const newCollection = reactive({
            name: '',
            description: ''
        });
        const activeCollection = ref(null);
        const collectionWords = ref([]);
        const wordToAdd = ref('');
        
        // Filter for words by collection
        const filteredByCollection = ref(null);
        
        // Modal window for editing collection
        const showEditCollection = ref(false);
        const editCollection = reactive({
            id: null,
            name: '',
            description: ''
        });
        
        // Modal window for word collections
        const showWordCollectionsModal = ref(false);
        const currentWord = ref('');
        const wordCollections = ref([]);
        const availableCollections = ref([]);
        const selectedCollection = ref('');
        
        // Notifications
        const notification = reactive({
            show: false,
            message: '',
            type: 'success', // success, error, warning
            timeout: null
        });
        
        // Computed properties
        const filteredWords = computed(() => {
            return words.value;
        });
        
        // API requests for words
        function getAllWords() {
            searchTerm.value = '';
            searched.value = false;
            filteredByCollection.value = null; // Clear collection filter
            
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
            filteredByCollection.value = null; // Clear collection filter
            
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
                    // Clear form
                    resetNewWord();
                    showAddWordForm.value = false;
                    // Update word list
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
                    // Clear form
                    resetNewMeaning();
                    showAddMeaningForm[response.data.word.id] = false;
                    // Update word list
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
                    // Clear input field
                    newExamples[meaningId] = '';
                    // Update word list
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
                    // Update word list
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
                    // Update word list
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
                    // Update word list
                    getAllWords();
                })
                .catch(error => {
                    showNotification(`Error deleting example: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        // API requests for collections
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
                    // Clear form
                    resetNewCollection();
                    showAddCollectionForm.value = false;
                    // Update collections
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
                    // Update collection details
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
                    // Update collection details
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
                    // Update collections
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
                    // Update collections
                    getAllCollections();
                })
                .catch(error => {
                    showNotification(`Error deleting collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        // Integration between words and collections
        function showWordCollections(wordStr) {
            currentWord.value = wordStr;
            showWordCollectionsModal.value = true;
            
            // Get collections for this word
            axios.get(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}/collections`)
                .then(response => {
                    wordCollections.value = response.data.collections;
                    // Get all collections to determine available ones
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
                    // Update collections for this word
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
                    // Update collections for this word
                    showWordCollections(currentWord.value);
                })
                .catch(error => {
                    showNotification(`Error removing from collection: ${getErrorMessage(error)}`, 'error');
                });
        }
        
        function viewCollectionWords(collection) {
            // Get words from collection
            axios.get(`${API_BASE_URL}/collections/${collection.id}`)
                .then(response => {
                    // Switch to words tab
                    activeTab.value = 'words';
                    
                    // Save collection details for filtering
                    filteredByCollection.value = {
                        id: collection.id,
                        name: collection.name
                    };
                    
                    // Get full information about each word in the collection
                    const wordsFromCollection = response.data.words;
                    if (wordsFromCollection && wordsFromCollection.length > 0) {
                        // Get full information for each word
                        const wordPromises = wordsFromCollection.map(word => 
                            axios.get(`${API_BASE_URL}/words/${encodeURIComponent(word.word)}`)
                                .then(resp => resp.data.word)
                        );
                        
                        Promise.all(wordPromises)
                            .then(wordDetails => {
                                words.value = wordDetails;
                                
                                // Update statistics
                                stats.value = {
                                    count: words.value.length,
                                    filtered_by: collection.name
                                };
                                
                                // Mark for displaying corresponding message
                                searchTerm.value = '';
                                searched.value = true;
                            })
                            .catch(error => {
                                showNotification(`Error loading word details: ${getErrorMessage(error)}`, 'error');
                            });
                    } else {
                        // No words in collection
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
        
        // Helper functions for UI
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
            // Clear existing timeout
            if (notification.timeout) {
                clearTimeout(notification.timeout);
            }
            
            // Set notification data
            notification.message = message;
            notification.type = type;
            notification.show = true;
            
            // Set timeout for hiding notification
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
        
        // Track changes
        watch(activeTab, (newTab) => {
            if (newTab === 'words') {
                getAllWords();
            } else if (newTab === 'collections') {
                getAllCollections();
            }
        });
        
        // Lifecycle hooks
        onMounted(() => {
            // Load words on component mount
            getAllWords();
            
            // Load collections
            getAllCollections();
        });
        
        // Return all necessary data and methods for use in template
        return {
            // States
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
            
            // Methods
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
            
            // Computed properties
            filteredWords
        };
    }
});

app.mount('#app'); 