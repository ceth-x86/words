const API_BASE_URL = '/api';

new Vue({
    el: '#app',
    data: {
        title: 'English Words Dictionary',
        words: [],
        searchTerm: '',
        searched: false,
        stats: null,
        
        // Tab navigation
        activeTab: 'words',
        
        // Words tab data
        showAddWordForm: false,
        showAddMeaningForm: {},
        newWord: {
            word: '',
            transcription: '',
            description: '',
            translation: '',
            examples: ''
        },
        newMeaning: {
            transcription: '',
            description: '',
            translation: '',
            examples: ''
        },
        newExamples: {},
        
        // Collections tab data
        collections: [],
        showAddCollectionForm: false,
        newCollection: {
            name: '',
            description: ''
        },
        activeCollection: null,
        collectionWords: [],
        wordToAdd: '',
        
        // Edit collection modal
        showEditCollection: false,
        editCollection: {
            id: null,
            name: '',
            description: ''
        },
        
        // Word collections modal
        showWordCollectionsModal: false,
        currentWord: '',
        wordCollections: [],
        availableCollections: [],
        selectedCollection: '',
        
        // Notifications
        notification: {
            show: false,
            message: '',
            type: 'success', // success, error, warning
            timeout: null
        }
    },
    methods: {
        // API Calls for Words
        getAllWords() {
            this.searchTerm = '';
            this.searched = false;
            axios.get(`${API_BASE_URL}/words`)
                .then(response => {
                    this.words = response.data.words;
                    this.stats = {
                        count: response.data.count,
                        total_meanings: response.data.total_meanings,
                        total_examples: response.data.total_examples
                    };
                })
                .catch(error => {
                    this.showNotification(`Error loading words: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        searchWords() {
            if (!this.searchTerm.trim()) {
                this.getAllWords();
                return;
            }
            
            this.searched = true;
            axios.get(`${API_BASE_URL}/search?term=${encodeURIComponent(this.searchTerm)}`)
                .then(response => {
                    this.words = response.data.words;
                    this.stats = { count: response.data.count };
                })
                .catch(error => {
                    this.showNotification(`Search error: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        addWord() {
            if (!this.newWord.word || !this.newWord.translation) {
                this.showNotification('Word and translation are required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/words`, this.newWord)
                .then(response => {
                    this.showNotification(`Word "${this.newWord.word}" added successfully`, 'success');
                    // Clear form
                    this.resetNewWord();
                    this.showAddWordForm = false;
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error adding word: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        addMeaning(wordStr) {
            if (!this.newMeaning.translation) {
                this.showNotification('Translation is required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}/meanings`, this.newMeaning)
                .then(response => {
                    this.showNotification(`New meaning added to "${wordStr}"`, 'success');
                    // Clear form
                    this.resetNewMeaning();
                    Vue.set(this.showAddMeaningForm, response.data.word.id, false);
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error adding meaning: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        addExample(meaningId) {
            const exampleText = this.newExamples[meaningId];
            if (!exampleText || !exampleText.trim()) {
                this.showNotification('Example text cannot be empty', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/meanings/${meaningId}/examples`, { text: exampleText })
                .then(response => {
                    this.showNotification('Example added successfully', 'success');
                    // Clear the input
                    Vue.set(this.newExamples, meaningId, '');
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error adding example: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        deleteWord(wordStr) {
            if (!confirm(`Are you sure you want to delete "${wordStr}" and all its meanings?`)) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}`)
                .then(response => {
                    this.showNotification(`Word "${wordStr}" deleted successfully`, 'success');
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error deleting word: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        deleteMeaning(meaningId) {
            if (!confirm('Are you sure you want to delete this meaning?')) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/meanings/${meaningId}`)
                .then(response => {
                    this.showNotification('Meaning deleted successfully', 'success');
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error deleting meaning: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        deleteExample(exampleId) {
            axios.delete(`${API_BASE_URL}/examples/${exampleId}`)
                .then(response => {
                    this.showNotification('Example deleted successfully', 'success');
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error deleting example: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        // API Calls for Collections
        getAllCollections() {
            axios.get(`${API_BASE_URL}/collections`)
                .then(response => {
                    this.collections = response.data.collections;
                })
                .catch(error => {
                    this.showNotification(`Error loading collections: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        addCollection() {
            if (!this.newCollection.name) {
                this.showNotification('Collection name is required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/collections`, this.newCollection)
                .then(response => {
                    this.showNotification(`Collection "${this.newCollection.name}" created successfully`, 'success');
                    // Clear form
                    this.resetNewCollection();
                    this.showAddCollectionForm = false;
                    // Refresh collections
                    this.getAllCollections();
                })
                .catch(error => {
                    this.showNotification(`Error creating collection: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        showCollectionDetails(collectionId) {
            axios.get(`${API_BASE_URL}/collections/${collectionId}`)
                .then(response => {
                    this.activeCollection = response.data.collection;
                    this.collectionWords = response.data.words;
                })
                .catch(error => {
                    this.showNotification(`Error loading collection: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        closeCollectionDetails() {
            this.activeCollection = null;
            this.collectionWords = [];
            this.wordToAdd = '';
        },
        
        addWordToCollection() {
            if (!this.wordToAdd.trim()) {
                this.showNotification('Word to add is required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/collections/${this.activeCollection.id}/words/${encodeURIComponent(this.wordToAdd)}`)
                .then(response => {
                    this.showNotification(`Word "${this.wordToAdd}" added to collection`, 'success');
                    this.wordToAdd = '';
                    // Refresh collection details
                    this.showCollectionDetails(this.activeCollection.id);
                })
                .catch(error => {
                    this.showNotification(`Error adding word to collection: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        removeWordFromCollection(wordStr) {
            if (!confirm(`Are you sure you want to remove "${wordStr}" from this collection?`)) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/collections/${this.activeCollection.id}/words/${encodeURIComponent(wordStr)}`)
                .then(response => {
                    this.showNotification(`Word "${wordStr}" removed from collection`, 'success');
                    // Refresh collection details
                    this.showCollectionDetails(this.activeCollection.id);
                })
                .catch(error => {
                    this.showNotification(`Error removing word from collection: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        showEditCollectionForm(collection) {
            this.editCollection = {
                id: collection.id,
                name: collection.name,
                description: collection.description || ''
            };
            this.showEditCollection = true;
        },
        
        closeEditCollection() {
            this.showEditCollection = false;
            this.editCollection = {
                id: null,
                name: '',
                description: ''
            };
        },
        
        updateCollection() {
            if (!this.editCollection.name) {
                this.showNotification('Collection name is required', 'warning');
                return;
            }
            
            axios.put(`${API_BASE_URL}/collections/${this.editCollection.id}`, {
                name: this.editCollection.name,
                description: this.editCollection.description
            })
                .then(response => {
                    this.showNotification('Collection updated successfully', 'success');
                    this.closeEditCollection();
                    // Refresh collections
                    this.getAllCollections();
                })
                .catch(error => {
                    this.showNotification(`Error updating collection: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        deleteCollection(collectionId) {
            if (!confirm('Are you sure you want to delete this collection?')) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/collections/${collectionId}`)
                .then(response => {
                    this.showNotification('Collection deleted successfully', 'success');
                    // Refresh collections
                    this.getAllCollections();
                })
                .catch(error => {
                    this.showNotification(`Error deleting collection: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        // Word-Collections integration
        showWordCollections(wordStr) {
            this.currentWord = wordStr;
            this.showWordCollectionsModal = true;
            
            // Get collections for this word
            axios.get(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}/collections`)
                .then(response => {
                    this.wordCollections = response.data.collections;
                    // Now get all collections to determine which ones are available
                    return axios.get(`${API_BASE_URL}/collections`);
                })
                .then(response => {
                    const allCollections = response.data.collections;
                    const wordCollectionIds = this.wordCollections.map(c => c.id);
                    this.availableCollections = allCollections.filter(
                        c => !wordCollectionIds.includes(c.id)
                    );
                })
                .catch(error => {
                    this.showNotification(`Error loading collections: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        closeWordCollections() {
            this.showWordCollectionsModal = false;
            this.currentWord = '';
            this.wordCollections = [];
            this.availableCollections = [];
            this.selectedCollection = '';
        },
        
        addToSelectedCollection() {
            if (!this.selectedCollection) {
                this.showNotification('Please select a collection', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/collections/${this.selectedCollection}/words/${encodeURIComponent(this.currentWord)}`)
                .then(response => {
                    this.showNotification(`Word added to collection successfully`, 'success');
                    // Refresh collections for this word
                    this.showWordCollections(this.currentWord);
                })
                .catch(error => {
                    this.showNotification(`Error adding to collection: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        removeFromCollection(collectionId) {
            axios.delete(`${API_BASE_URL}/collections/${collectionId}/words/${encodeURIComponent(this.currentWord)}`)
                .then(response => {
                    this.showNotification(`Word removed from collection successfully`, 'success');
                    // Refresh collections for this word
                    this.showWordCollections(this.currentWord);
                })
                .catch(error => {
                    this.showNotification(`Error removing from collection: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        // UI Helpers
        resetNewWord() {
            this.newWord = {
                word: '',
                transcription: '',
                description: '',
                translation: '',
                examples: ''
            };
        },
        
        resetNewMeaning() {
            this.newMeaning = {
                transcription: '',
                description: '',
                translation: '',
                examples: ''
            };
        },
        
        resetNewCollection() {
            this.newCollection = {
                name: '',
                description: ''
            };
        },
        
        showNotification(message, type = 'success') {
            // Clear any existing timeout
            if (this.notification.timeout) {
                clearTimeout(this.notification.timeout);
            }
            
            // Set notification data
            this.notification.message = message;
            this.notification.type = type;
            this.notification.show = true;
            
            // Set timeout to hide notification
            this.notification.timeout = setTimeout(() => {
                this.notification.show = false;
            }, 3000);
        },
        
        getErrorMessage(error) {
            if (error.response && error.response.data && error.response.data.error) {
                return error.response.data.error;
            }
            return error.message || 'Unknown error';
        }
    },
    mounted() {
        // Load words when the component is mounted
        this.getAllWords();
        
        // Load collections
        this.getAllCollections();
    },
    watch: {
        // When tab changes, load appropriate data
        activeTab(newTab) {
            if (newTab === 'words') {
                this.getAllWords();
            } else if (newTab === 'collections') {
                this.getAllCollections();
            }
        }
    }
}); 