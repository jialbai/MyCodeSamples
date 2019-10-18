import string
import gensim
import nltk
import numpy as np
from keras import regularizers
from keras.callbacks import ModelCheckpoint
from keras.layers import Conv1D, MaxPooling1D
from keras.layers import Dropout, Flatten, Dense, Input, concatenate
from keras.models import Model
from keras.utils import np_utils
from nltk.corpus import stopwords
from sklearn.datasets import fetch_20newsgroups
from sklearn.datasets import load_files
from sklearn.feature_extraction.text import CountVectorizer
from sklearn.metrics import accuracy_score


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
    # stemmer = PorterStemmer()
    # stemmed = stem_tokens(filtered, stemmer)
    str = ' '.join(filtered)
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

def load(filename):
 
    # Input: GloVe Model File
    # More models can be downloaded from http://nlp.stanford.edu/projects/glove/
    # glove_file="glove.840B.300d.txt"
    glove_file = filename
 
    dimensions = 300
 
    num_lines = getFileLineNums(filename)
    # num_lines = check_num_lines_in_glove(glove_file)
    # dims = int(dimensions[:-1])
    dims = 300
 
    print(num_lines)
    #
    # # Output: Gensim Model text format.
    gensim_file='w2v_model/glove_gensim.txt'
    gensim_first_line = "{} {}".format(num_lines, dims)
    #
    # # Prepends the line.
    prepend_slow(glove_file, gensim_file, gensim_first_line)
 
    # Demo: Loads the newly created glove_model.txt into gensim API.
    model=gensim.models.KeyedVectors.load_word2vec_format(gensim_file,binary=False) #GloVe Model
 
    model_name = filename[-16:-4]
    
    model.save('w2v_model/' + model_name)
 
    return model

def getFileLineNums(filename):
    f = open(filename,'r')
    count = 0
    for line in f:
        count += 1
    return count

def prepend_line(infile, outfile, line):
    with open(infile, 'r') as old:
        with open(outfile, 'w') as new:
            new.write(str(line) + "\n")
            shutil.copyfileobj(old, new)
 
def prepend_slow(infile, outfile, line):
    with open(infile, 'r') as fin:
        with open(outfile, 'w') as fout:
            fout.write(line + "\n")
            for line in fin:
                fout.write(line)
def toOneHotList(list):
    oneHotList = []
    for i in range(len(list)):
        tmpVec = [ 0 for i in range(20)]
        tmpVec[list[i]] = 1
        oneHotList.append(tmpVec)
    return oneHotList



if __name__ == '__main__':

    print("start pre processing......")
    newsgroup_train = fetch_20newsgroups(subset = 'train');
    newsgroup_test = fetch_20newsgroups(subset = 'test');

    vectorizer = CountVectorizer(stop_words='english')
    analyzer = vectorizer.build_analyzer()

    corpus_train_solution2_sentences = []
    for i in range(len(newsgroup_train.data)):
        corpus_train_solution2_sentences.append(analyzer(newsgroup_train.data[i]))

    corpus_test_solution2_sentences = []
    for i in range(len(newsgroup_test.data)):
        corpus_test_solution2_sentences.append(analyzer(newsgroup_test.data[i]))


    y_train_numeric = newsgroup_train.target
    y_test_numeric = newsgroup_test.target
    y_train_onehot = np.array(toOneHotList(newsgroup_train.target))
    y_test_onehot = np.array(toOneHotList(newsgroup_test.target))
    print("pre processing completed......")


    print('start to load glo vec!!!')
    model = load('w2v_model/glove.840B.300d.txt')
    # model = gensim.models.KeyedVectors.load_word2vec_format('w2v_model/glove_gensim.txt',binary=False)
    print(model['school'])
    print('glove vec model trained!!!')
    
    n_dimension = 300
    n_word_count = 200


    # sentences_text8_solution2 = word2vec.Text8Corpus('./text8')

    # model = word2vec.Word2Vec(sentences_text8_solution2,size=n_dimension,window=5,min_count=5,workers=multiprocessing.cpu_count())
    # model.save('./w2v_model/w2v_conv1d_text8_1.model')

    doc_train_set_vectorized_2D = reprensent_all_doc_set_in_2D_array(corpus_train_solution2_sentences)
    doc_test_set_vectorized_2D = reprensent_all_doc_set_in_2D_array(corpus_test_solution2_sentences)

    X_train_solution2 = np.array(doc_train_set_vectorized_2D)
    X_test_solution2 = np.array(doc_test_set_vectorized_2D)

    print('X_train y_train X_test y_test prepared!!!')


    my_cnn_model = text_cnn()
    my_cnn_model.summary()

    epochs = 50

    checkpointer = ModelCheckpoint(filepath='./saved_cnn_models/weights.best.textcnn_adadelta_glove.hdf5', 
                               verbose=1, save_best_only=True)

    # log_filepath = 'tmp/keras_log'
    # tb_cb = TensorBoard(log_dir=log_filepath, write_images=1, histogram_freq=1)
    

    my_cnn_model.fit(X_train_solution2, y_train_onehot, 
          validation_split=0.10,
          epochs=epochs, callbacks=[checkpointer], batch_size=50, verbose=1)
    # callbacks=[checkpointer]

    my_cnn_model.load_weights('./saved_cnn_models/weights.best.textcnn_adadelta_glove.hdf5')

    print('training completed')

    y_predict_onehot = my_cnn_model.predict(X_test_solution2)
    y_predict_numeric = one_hot_to_numeric(y_predict_onehot)


    score_solution2 = accuracy_score(y_test_numeric, y_predict_numeric)
    print("TextCNN model with GloVe has accuracy score {:,.2f} on test data".format(score_solution2))
