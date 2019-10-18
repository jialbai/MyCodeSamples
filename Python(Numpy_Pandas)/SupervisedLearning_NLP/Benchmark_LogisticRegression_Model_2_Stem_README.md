# Preconditions before running Benchmark_Model_2_stem.py：


## Files to prepare:

1.	Download 20news-bydate dataset (train+test)
2.	import JialiangBAI_TextCNN_LR.yaml for anaconda
3.	For pre-processing of words, install nltk，stopwords and punkt
	import nltk
	nltk.download('stopwords')
	nltk.download('punkt')


## Tree of files:
.
+-- Benchmark_Model_2_stem.py
+-- 20news-bydate
|   +-- 20news-bydate-test
|   +--   +-- alt.atheism
|   +--   +-- comp.graphics
|   +--   +-- comp.os.ms-windows.misc
|   +--   +-- ... ... 
|   +-- 20news-bydate-train
|   +--   +-- alt.atheism
|   +--   +-- comp.graphics
|   +--   +-- comp.os.ms-windows.misc
|   +--   +-- ... ... 
