import string
from glob import glob

import chardet
import nltk
import numpy as np
from keras import regularizers
from keras.layers import Conv1D, MaxPooling1D
from keras.layers import Dropout, Flatten, Dense, Input, concatenate
from keras.models import Model
from keras.utils import np_utils
from nltk.corpus import stopwords
from nltk.stem.porter import *
from sklearn.datasets import load_files
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score
import warnings


def load_dataset(path):
    data = load_files(path)
    doc_files = np.array(data['filenames'])
    doc_targets = np_utils.to_categorical(np.array(data['target']), 20)
    return doc_files, doc_targets


def get_tokens(text):
    lowers = text.lower()
    remove_punctuation_map = dict((ord(char), None) for char in string.punctuation)
    no_punctuation = lowers.translate(remove_punctuation_map)
    tokens = nltk.word_tokenize(no_punctuation)
    return tokens

def stem_tokens(tokens, stemmer):
    stemmed = []
    for item in tokens:
        stemmed.append(stemmer.stem(item))
    return stemmed

def one_hot_to_numeric(y_label):
    y_numeric = np.arange(len(y_label))
    for i in range(len(y_label)):
        y_numeric[i] = (np.argmax(y_label[i]))
    return y_numeric

def preProcessor(text):
    tokens = get_tokens(text)
    filtered = [w for w in tokens if not w in stopwords.words('english')]
    stemmer = PorterStemmer()
    stemmed = stem_tokens(filtered, stemmer)
    str = ' '.join(stemmed)
    return str

def represent_single_doc_in_2D_array(doc_in_array):
    doc_vec_array = np.zeros((n_word_count, n_dimension))
    for i in range(n_word_count):
        if(i < len(doc_in_array)):
            try:
                tmp_word_vec = np.array(model[doc_in_array[i]])
            except KeyError:
                continue
            doc_vec_array[i] = tmp_word_vec
        else:
            doc_vec_array[i] = np.zeros(n_dimension)
    return doc_vec_array

def reprensent_all_doc_set_in_2D_array(doc_set):
    doc_set_vec = []
    np.zeros((len(doc_set),n_dimension))
    for i in range(len(doc_set)):
        single_doc_vec = represent_single_doc_in_2D_array(doc_set[i])
        doc_set_vec.append(single_doc_vec)
    return doc_set_vec


def represent_single_text8_doc(doc_in_array):
    doc_vec = np.zeros(n_dimension)
    for i in range(len(doc_in_array)):
        try:
            tmp_word_vec = np.array(text8_model[doc_in_array[i]])
        except KeyError:
            tmp_word_vec = np.zeros(n_dimension)
        doc_vec += tmp_word_vec
        
    return doc_vec/float(len(doc_in_array))

def reprensent_text8_doc_set(doc_set):
    doc_set_vec = np.zeros((len(doc_set),n_dimension))
    for i in range(len(doc_set)):
        doc_vec = represent_single_text8_doc(doc_set[i])
        doc_set_vec[i] = doc_vec
    return doc_set_vec

def text_cnn(maxlen=200, max_features=300, embed_size=32):
    comment_seq = Input(shape=[maxlen,max_features], name='x_seq')
    convs = []
    filter_sizes = [3, 4, 5]
    for fsz in filter_sizes:
        l_conv = Conv1D(filters=128, kernel_size=fsz, activation='tanh')(comment_seq)
        l_conv = Dropout(0.5)(l_conv)
        l_pool = MaxPooling1D(maxlen - fsz + 1)(l_conv)
        l_pool = Flatten()(l_pool)
        convs.append(l_pool)
    merge = concatenate(convs, axis=1)
    merge = Dropout(0.6)(merge)
    merge = Dense(128, activation='tanh', kernel_regularizer=regularizers.l2(0.01))(merge)    
    output = Dense(20, activation='softmax')(merge)
    model = Model([comment_seq], output)
    model.compile(optimizer='Adadelta', loss='categorical_crossentropy', metrics=['accuracy'])
    return model

if __name__ == '__main__':
    warnings.filterwarnings('ignore')
    print('... ... ... ... start loading dataset ... ... ... ...')

    train_files, train_targets = load_dataset('20news-bydate/20news-bydate-train')
    test_files, test_targets = load_dataset('20news-bydate/20news-bydate-test')

    
    category_names = [item[20:-1] for item in sorted(glob("20news-bydate/20news-bydate-train/*/"))]

    
    print('There are %d total doc categories.' % len(category_names))
    print('There are %s total docs.' % len(np.hstack([train_files, test_files])))
    print('There are %d training docs.' % len(train_files))
    print('There are %d test docs.'% len(test_files))

    corpus_train = []
    for i in range(len(train_files)):
        try:
            with open(train_files[i], 'rb') as f:
                text = f.read()
                encoding_pattern = chardet.detect(text)
                content = text.decode(encoding=encoding_pattern['encoding'])
                content = preProcessor(content)
                corpus_train.append(content)
                f.close()
        except TypeError:
            with open(train_files[i], 'rb') as f:
                text = f.read()
                encoding_pattern = chardet.detect(text)
                content = text.decode('utf8')
                content = preProcessor(content)
                corpus_train.append(content)
                f.close()

    print('len of corpus_train is {}'.format(len(corpus_train)))

    corpus_test = []
    for i in range(len(test_files)):
        try:
            with open(test_files[i], 'rb') as f:
                text = f.read()
                encoding_pattern = chardet.detect(text)
                content = text.decode(encoding=encoding_pattern['encoding'])
                content = preProcessor(content)
                corpus_test.append(content)
                f.close()
        except TypeError:
            with open(test_files[i], 'rb') as f:
                text = f.read()
                encoding_pattern = chardet.detect(text)
                content = text.decode('utf8')
                content = preProcessor(content)
                corpus_test.append(content)
                f.close()

    print('len of corpus_test is {}'.format(len(corpus_test)))

    print('... ... ... ... finish loading dataset ... ... ... ...')

    print('... ... ... ... start pre processing data ... ... ... ...')

    y_train = train_targets

    y_test = test_targets


    y_train_numeric = one_hot_to_numeric(y_train)

    y_test_numeric = one_hot_to_numeric(y_test)

    print('... ... ... ... finish pre processing data ... ... ... ...')


    print('... ... ... ... start preparing X_train, X_test, y_train, y_test ... ... ... ...')    
    vectorizer = TfidfVectorizer(min_df=1)
    X_train = vectorizer.fit_transform(corpus_train).toarray()

    vectorizer_test = TfidfVectorizer(vocabulary = vectorizer.vocabulary_);
    X_test = vectorizer_test.fit_transform(corpus_test).toarray()
    print('... ... ... ... finish preparing X_train, X_test, y_train, y_test ... ... ... ...')    


    print('... ... ... ... start building logistic regression model ... ... ... ...')
    lr = LogisticRegression(C=1, class_weight=None, dual=False, fit_intercept=True,
          intercept_scaling=1, max_iter=3, multi_class='multinomial',
          n_jobs=1, penalty='l2', random_state=18, solver='sag',
          tol=0.0001, verbose=0, warm_start=False)
    print('... ... ... ... finish building logistic regression model ... ... ... ...')

    print('... ... ... ... start training logistic regression model ... ... ... ...')
    lr.fit(X_train, y_train_numeric)
    print('... ... ... ... finish training logistic regression model ... ... ... ...')


    y_true = y_test_numeric
    print('... ... ... ... predict on test set and compute the accuracy score ... ... ... ...')
    y_predict = lr.predict(X_test)

    score = accuracy_score(y_true, y_predict)
    print("Stemmed benchmark model has accuracy score {:,.2f} on test data".format(score))
