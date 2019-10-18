
# coding: utf-8

# ## 卷积神经网络（Convolutional Neural Network, CNN）
# 
# ## 项目：实现一个狗品种识别算法App
# 
# 在这个notebook文件中，有些模板代码已经提供给你，但你还需要实现更多的功能来完成这个项目。除非有明确要求，你无须修改任何已给出的代码。以**'(练习)'**开始的标题表示接下来的代码部分中有你需要实现的功能。这些部分都配有详细的指导，需要实现的部分也会在注释中以'TODO'标出。请仔细阅读所有的提示。
# 
# 除了实现代码外，你还**需要**回答一些与项目及代码相关的问题。每个需要回答的问题都会以 **'问题 X'** 标记。请仔细阅读每个问题，并且在问题后的 **'回答'** 部分写出完整的答案。我们将根据 你对问题的回答 和 撰写代码实现的功能 来对你提交的项目进行评分。
# 
# >**提示：**Code 和 Markdown 区域可通过 **Shift + Enter** 快捷键运行。此外，Markdown可以通过双击进入编辑模式。
# 
# 项目中显示为_选做_的部分可以帮助你的项目脱颖而出，而不是仅仅达到通过的最低要求。如果你决定追求更高的挑战，请在此 notebook 中完成_选做_部分的代码。
# 
# ---
# 
# ### 让我们开始吧
# 在这个notebook中，你将迈出第一步，来开发可以作为移动端或 Web应用程序一部分的算法。在这个项目的最后，你的程序将能够把用户提供的任何一个图像作为输入。如果可以从图像中检测到一只狗，它会输出对狗品种的预测。如果图像中是一个人脸，它会预测一个与其最相似的狗的种类。下面这张图展示了完成项目后可能的输出结果。（……实际上我们希望每个学生的输出结果不相同！）
# 
# ![Sample Dog Output](images/sample_dog_output.png)
# 
# 在现实世界中，你需要拼凑一系列的模型来完成不同的任务；举个例子，用来预测狗种类的算法会与预测人类的算法不同。在做项目的过程中，你可能会遇到不少失败的预测，因为并不存在完美的算法和模型。你最终提交的不完美的解决方案也一定会给你带来一个有趣的学习经验！
# 
# ### 项目内容
# 
# 我们将这个notebook分为不同的步骤，你可以使用下面的链接来浏览此notebook。
# 
# * [Step 0](#step0): 导入数据集
# * [Step 1](#step1): 检测人脸
# * [Step 2](#step2): 检测狗狗
# * [Step 3](#step3): 从头创建一个CNN来分类狗品种
# * [Step 4](#step4): 使用一个CNN来区分狗的品种(使用迁移学习)
# * [Step 5](#step5): 建立一个CNN来分类狗的品种（使用迁移学习）
# * [Step 6](#step6): 完成你的算法
# * [Step 7](#step7): 测试你的算法
# 
# 在该项目中包含了如下的问题：
# 
# * [问题 1](#question1)
# * [问题 2](#question2)
# * [问题 3](#question3)
# * [问题 4](#question4)
# * [问题 5](#question5)
# * [问题 6](#question6)
# * [问题 7](#question7)
# * [问题 8](#question8)
# * [问题 9](#question9)
# * [问题 10](#question10)
# * [问题 11](#question11)
# 
# 
# ---
# <a id='step0'></a>
# ## 步骤 0: 导入数据集
# 
# ### 导入狗数据集
# 在下方的代码单元（cell）中，我们导入了一个狗图像的数据集。我们使用 scikit-learn 库中的 `load_files` 函数来获取一些变量：
# - `train_files`, `valid_files`, `test_files` - 包含图像的文件路径的numpy数组
# - `train_targets`, `valid_targets`, `test_targets` - 包含独热编码分类标签的numpy数组
# - `dog_names` - 由字符串构成的与标签相对应的狗的种类

# In[1]:


from sklearn.datasets import load_files       
from keras.utils import np_utils
import numpy as np
from glob import glob

# 定义函数来加载train，test和validation数据集
def load_dataset(path):
    data = load_files(path)
    dog_files = np.array(data['filenames'])
    dog_targets = np_utils.to_categorical(np.array(data['target']), 133)
    return dog_files, dog_targets

# 加载train，test和validation数据集
train_files, train_targets = load_dataset('dogImages/train')
valid_files, valid_targets = load_dataset('dogImages/valid')
test_files, test_targets = load_dataset('dogImages/test')

# 加载狗品种列表
dog_names = [item[20:-1] for item in sorted(glob("dogImages/train/*/"))]

# 打印数据统计描述
print('There are %d total dog categories.' % len(dog_names))
print('There are %s total dog images.\n' % len(np.hstack([train_files, valid_files, test_files])))
print('There are %d training dog images.' % len(train_files))
print('There are %d validation dog images.' % len(valid_files))
print('There are %d test dog images.'% len(test_files))


# ### 导入人脸数据集
# 
# 在下方的代码单元中，我们导入人脸图像数据集，文件所在路径存储在名为 `human_files` 的 numpy 数组。

# In[2]:


import random
random.seed(8675309)

# 加载打乱后的人脸数据集的文件名
human_files = np.array(glob("lfw/*/*"))
random.shuffle(human_files)

# 打印数据集的数据量
print('There are %d total human images.' % len(human_files))


# ---
# <a id='step1'></a>
# ## 步骤1：检测人脸
#  
# 我们将使用 OpenCV 中的 [Haar feature-based cascade classifiers](http://docs.opencv.org/trunk/d7/d8b/tutorial_py_face_detection.html) 来检测图像中的人脸。OpenCV 提供了很多预训练的人脸检测模型，它们以XML文件保存在 [github](https://github.com/opencv/opencv/tree/master/data/haarcascades)。我们已经下载了其中一个检测模型，并且把它存储在 `haarcascades` 的目录中。
# 
# 在如下代码单元中，我们将演示如何使用这个检测模型在样本图像中找到人脸。

# In[3]:


import cv2                
import matplotlib.pyplot as plt                        
get_ipython().run_line_magic('matplotlib', 'inline')

# 提取预训练的人脸检测模型
face_cascade = cv2.CascadeClassifier('haarcascades/haarcascade_frontalface_alt.xml')

# 加载彩色（通道顺序为BGR）图像
img = cv2.imread(human_files[3])

# 将BGR图像进行灰度处理
gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

# 在图像中找出脸
faces = face_cascade.detectMultiScale(gray)

# 打印图像中检测到的脸的个数
print('Number of faces detected:', len(faces))

# 获取每一个所检测到的脸的识别框
for (x,y,w,h) in faces:
    # 在人脸图像中绘制出识别框
    cv2.rectangle(img,(x,y),(x+w,y+h),(255,0,0),2)
    
# 将BGR图像转变为RGB图像以打印
cv_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

# 展示含有识别框的图像
plt.imshow(cv_rgb)
plt.show()


# 在使用任何一个检测模型之前，将图像转换为灰度图是常用过程。`detectMultiScale` 函数使用储存在 `face_cascade` 中的的数据，对输入的灰度图像进行分类。
# 
# 在上方的代码中，`faces` 以 numpy 数组的形式，保存了识别到的面部信息。它其中每一行表示一个被检测到的脸，该数据包括如下四个信息：前两个元素  `x`、`y` 代表识别框左上角的 x 和 y 坐标（参照上图，注意 y 坐标的方向和我们默认的方向不同）；后两个元素代表识别框在 x 和 y 轴两个方向延伸的长度 `w` 和 `d`。 
# 
# ### 写一个人脸识别器
# 
# 我们可以将这个程序封装为一个函数。该函数的输入为人脸图像的**路径**，当图像中包含人脸时，该函数返回 `True`，反之返回 `False`。该函数定义如下所示。

# In[4]:


# 如果img_path路径表示的图像检测到了脸，返回"True" 
def face_detector(img_path):
    img = cv2.imread(img_path)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    faces = face_cascade.detectMultiScale(gray)
    return len(faces) > 0


# ### **【练习】** 评估人脸检测模型

# 
# ---
# 
# <a id='question1'></a>
# ### __问题 1:__ 
# 
# 在下方的代码块中，使用 `face_detector` 函数，计算：
# 
# - `human_files` 的前100张图像中，能够检测到**人脸**的图像占比多少？
# - `dog_files` 的前100张图像中，能够检测到**人脸**的图像占比多少？
# 
# 理想情况下，人图像中检测到人脸的概率应当为100%，而狗图像中检测到人脸的概率应该为0%。你会发现我们的算法并非完美，但结果仍然是可以接受的。我们从每个数据集中提取前100个图像的文件路径，并将它们存储在`human_files_short`和`dog_files_short`中。

# In[5]:


human_files_short = human_files[:100]
dog_files_short = train_files[:100]
## 请不要修改上方代码


## TODO: 基于human_files_short和dog_files_short
## 中的图像测试face_detector的表现
n_faces_from_human_file = 0
n_faces_from_dog_file = 0
for human_path in human_files_short:
    if (face_detector(human_path)):
        n_faces_from_human_file += 1
        
for dog_path in dog_files_short:
    if (face_detector(dog_path)):
        n_faces_from_dog_file += 1
        
print('human_files 的前100张图像中，能够检测到人脸的图像占比{}%'.format(n_faces_from_human_file))
print('dog_files 的前100张图像中，能够检测到人脸的图像占比{}%'.format(n_faces_from_dog_file))


# ---
# 
# <a id='question2'></a>
# 
# ### __问题 2:__ 
# 
# 就算法而言，该算法成功与否的关键在于，用户能否提供含有清晰面部特征的人脸图像。
# 那么你认为，这样的要求在实际使用中对用户合理吗？如果你觉得不合理，你能否想到一个方法，即使图像中并没有清晰的面部特征，也能够检测到人脸？
# 
# __回答:__
# 
# 实际使用中，尽管我们会要求用户尽量提供清晰的人脸图像，但用户提供的照片不一定会含有清晰的面部特征，所以我们要对用户没有提供清晰面部特征的情况做出一定的容错处理（即“即使图像中并没有清晰的面部特征，也能够检测到人脸”）。
# 
# 可能的容错方案：如果不能检测到人脸，可以让用户再次上传，直到可以检测为人脸为止。
# 
# 
# 
# 

# ---
# 
# <a id='Selection1'></a>
# ### 选做：
# 
# 我们建议在你的算法中使用opencv的人脸检测模型去检测人类图像，不过你可以自由地探索其他的方法，尤其是尝试使用深度学习来解决它:)。请用下方的代码单元来设计和测试你的面部监测算法。如果你决定完成这个_选做_任务，你需要报告算法在每一个数据集上的表现。

# In[ ]:


## (选做) TODO: 报告另一个面部检测算法在LFW数据集上的表现
### 你可以随意使用所需的代码单元数


# ---
# <a id='step2'></a>
# 
# ## 步骤 2: 检测狗狗
# 
# 在这个部分中，我们使用预训练的 [ResNet-50](http://ethereon.github.io/netscope/#/gist/db945b393d40bfa26006) 模型去检测图像中的狗。下方的第一行代码就是下载了 ResNet-50 模型的网络结构参数，以及基于 [ImageNet](http://www.image-net.org/) 数据集的预训练权重。
# 
# ImageNet 这目前一个非常流行的数据集，常被用来测试图像分类等计算机视觉任务相关的算法。它包含超过一千万个 URL，每一个都链接到 [1000 categories](https://gist.github.com/yrevar/942d3a0ac09ec9e5eb3a) 中所对应的一个物体的图像。任给输入一个图像，该 ResNet-50 模型会返回一个对图像中物体的预测结果。

# In[6]:


from keras.applications.resnet50 import ResNet50

# 定义ResNet50模型
ResNet50_model = ResNet50(weights='imagenet')


# ### 数据预处理
# 
# - 在使用 TensorFlow 作为后端的时候，在 Keras 中，CNN 的输入是一个4维数组（也被称作4维张量），它的各维度尺寸为 `(nb_samples, rows, columns, channels)`。其中 `nb_samples` 表示图像（或者样本）的总数，`rows`, `columns`, 和 `channels` 分别表示图像的行数、列数和通道数。
# 
# 
# - 下方的 `path_to_tensor` 函数实现如下将彩色图像的字符串型的文件路径作为输入，返回一个4维张量，作为 Keras CNN 输入。因为我们的输入图像是彩色图像，因此它们具有三个通道（ `channels` 为 `3`）。
#     1. 该函数首先读取一张图像，然后将其缩放为 224×224 的图像。
#     2. 随后，该图像被调整为具有4个维度的张量。
#     3. 对于任一输入图像，最后返回的张量的维度是：`(1, 224, 224, 3)`。
# 
# 
# - `paths_to_tensor` 函数将图像路径的字符串组成的 numpy 数组作为输入，并返回一个4维张量，各维度尺寸为 `(nb_samples, 224, 224, 3)`。 在这里，`nb_samples`是提供的图像路径的数据中的样本数量或图像数量。你也可以将 `nb_samples` 理解为数据集中3维张量的个数（每个3维张量表示一个不同的图像。

# In[7]:


from keras.preprocessing import image                  
from tqdm import tqdm

def path_to_tensor(img_path):
    # 用PIL加载RGB图像为PIL.Image.Image类型
    img = image.load_img(img_path, target_size=(224, 224))
    # 将PIL.Image.Image类型转化为格式为(224, 224, 3)的3维张量
    x = image.img_to_array(img)
    # 将3维张量转化为格式为(1, 224, 224, 3)的4维张量并返回
    return np.expand_dims(x, axis=0)

def paths_to_tensor(img_paths):
    list_of_tensors = [path_to_tensor(img_path) for img_path in tqdm(img_paths)]
    return np.vstack(list_of_tensors)


# ### 基于 ResNet-50 架构进行预测
# 
# 对于通过上述步骤得到的四维张量，在把它们输入到 ResNet-50 网络、或 Keras 中其他类似的预训练模型之前，还需要进行一些额外的处理：
# 1. 首先，这些图像的通道顺序为 RGB，我们需要重排他们的通道顺序为 BGR。
# 2. 其次，预训练模型的输入都进行了额外的归一化过程。因此我们在这里也要对这些张量进行归一化，即对所有图像所有像素都减去像素均值 `[103.939, 116.779, 123.68]`（以 RGB 模式表示，根据所有的 ImageNet 图像算出）。
# 
# 导入的 `preprocess_input` 函数实现了这些功能。如果你对此很感兴趣，可以在 [这里](https://github.com/fchollet/keras/blob/master/keras/applications/imagenet_utils.py) 查看 `preprocess_input`的代码。
# 
# 
# 在实现了图像处理的部分之后，我们就可以使用模型来进行预测。这一步通过 `predict` 方法来实现，它返回一个向量，向量的第 i 个元素表示该图像属于第 i 个 ImageNet 类别的概率。这通过如下的 `ResNet50_predict_labels` 函数实现。
# 
# 通过对预测出的向量取用 argmax 函数（找到有最大概率值的下标序号），我们可以得到一个整数，即模型预测到的物体的类别。进而根据这个 [清单](https://gist.github.com/yrevar/942d3a0ac09ec9e5eb3a)，我们能够知道这具体是哪个品种的狗狗。
# 

# In[8]:


from keras.applications.resnet50 import preprocess_input, decode_predictions
def ResNet50_predict_labels(img_path):
    # 返回img_path路径的图像的预测向量
    img = preprocess_input(path_to_tensor(img_path))
    return np.argmax(ResNet50_model.predict(img))


# ### 完成狗检测模型
# 
# 
# 在研究该 [清单](https://gist.github.com/yrevar/942d3a0ac09ec9e5eb3a) 的时候，你会注意到，狗类别对应的序号为151-268。因此，在检查预训练模型判断图像是否包含狗的时候，我们只需要检查如上的 `ResNet50_predict_labels` 函数是否返回一个介于151和268之间（包含区间端点）的值。
# 
# 我们通过这些想法来完成下方的 `dog_detector` 函数，如果从图像中检测到狗就返回 `True`，否则返回 `False`。

# In[9]:


def dog_detector(img_path):
    prediction = ResNet50_predict_labels(img_path)
    return ((prediction <= 268) & (prediction >= 151)) 


# ### 【作业】评估狗狗检测模型
# 
# ---
# 
# <a id='question3'></a>
# ### __问题 3:__ 
# 
# 在下方的代码块中，使用 `dog_detector` 函数，计算：
# 
# - `human_files_short`中图像检测到狗狗的百分比？
# - `dog_files_short`中图像检测到狗狗的百分比？

# In[10]:


### TODO: 测试dog_detector函数在human_files_short和dog_files_short的表现
n_dog_from_human_file = 0
n_dog_from_dog_file = 0
for human_path in human_files_short:
    if (dog_detector(human_path)):
        n_dog_from_human_file += 1
        
for dog_path in dog_files_short:
    if (dog_detector(dog_path)):
        n_dog_from_dog_file += 1
        
print('human_files_short中图像检测到狗狗的百分比{}%'.format(n_dog_from_human_file))
print('dog_files_short中图像检测到狗狗的百分比{}%'.format(n_dog_from_dog_file))


# ---
# 
# <a id='step3'></a>
# 
# ## 步骤 3: 从头开始创建一个CNN来分类狗品种
# 
# 
# 现在我们已经实现了一个函数，能够在图像中识别人类及狗狗。但我们需要更进一步的方法，来对狗的类别进行识别。在这一步中，你需要实现一个卷积神经网络来对狗的品种进行分类。你需要__从头实现__你的卷积神经网络（在这一阶段，你还不能使用迁移学习），并且你需要达到超过1%的测试集准确率。在本项目的步骤五种，你还有机会使用迁移学习来实现一个准确率大大提高的模型。
# 
# 在添加卷积层的时候，注意不要加上太多的（可训练的）层。更多的参数意味着更长的训练时间，也就是说你更可能需要一个 GPU 来加速训练过程。万幸的是，Keras 提供了能够轻松预测每次迭代（epoch）花费时间所需的函数。你可以据此推断你算法所需的训练时间。
# 
# 值得注意的是，对狗的图像进行分类是一项极具挑战性的任务。因为即便是一个正常人，也很难区分布列塔尼犬和威尔士史宾格犬。
# 
# 
# 布列塔尼犬（Brittany） | 威尔士史宾格犬（Welsh Springer Spaniel）
# - | - 
# <img src="images/Brittany_02625.jpg" width="100"> | <img src="images/Welsh_springer_spaniel_08203.jpg" width="200">
# 
# 不难发现其他的狗品种会有很小的类间差别（比如金毛寻回犬和美国水猎犬）。
# 
# 
# 金毛寻回犬（Curly-Coated Retriever） | 美国水猎犬（American Water Spaniel）
# - | -
# <img src="images/Curly-coated_retriever_03896.jpg" width="200"> | <img src="images/American_water_spaniel_00648.jpg" width="200">
# 
# 同样，拉布拉多犬（labradors）有黄色、棕色和黑色这三种。那么你设计的基于视觉的算法将不得不克服这种较高的类间差别，以达到能够将这些不同颜色的同类狗分到同一个品种中。
# 
# 黄色拉布拉多犬（Yellow Labrador） | 棕色拉布拉多犬（Chocolate Labrador） | 黑色拉布拉多犬（Black Labrador）
# - | -
# <img src="images/Labrador_retriever_06457.jpg" width="150"> | <img src="images/Labrador_retriever_06455.jpg" width="240"> | <img src="images/Labrador_retriever_06449.jpg" width="220">
# 
# 我们也提到了随机分类将得到一个非常低的结果：不考虑品种略有失衡的影响，随机猜测到正确品种的概率是1/133，相对应的准确率是低于1%的。
# 
# 请记住，在深度学习领域，实践远远高于理论。大量尝试不同的框架吧，相信你的直觉！当然，玩得开心！
# 
# 
# ### 数据预处理
# 
# 
# 通过对每张图像的像素值除以255，我们对图像实现了归一化处理。

# In[11]:


from PIL import ImageFile                            
ImageFile.LOAD_TRUNCATED_IMAGES = True                 

# Keras中的数据预处理过程
train_tensors = paths_to_tensor(train_files).astype('float32')/255
valid_tensors = paths_to_tensor(valid_files).astype('float32')/255
test_tensors = paths_to_tensor(test_files).astype('float32')/255


# ### 【练习】模型架构
# 
# 
# 创建一个卷积神经网络来对狗品种进行分类。在你代码块的最后，执行 `model.summary()` 来输出你模型的总结信息。
#     
# 我们已经帮你导入了一些所需的 Python 库，如有需要你可以自行导入。如果你在过程中遇到了困难，如下是给你的一点小提示——该模型能够在5个 epoch 内取得超过1%的测试准确率，并且能在CPU上很快地训练。
# 
# ![Sample CNN](images/sample_cnn.png)

# 经观察，
# 1. 每个池化层(max_pooling2d)的pool_size都是2*2，因为最大池化后的特征图size变化为223->111(110)->55(54)->27
# 2. 第一个卷积层的参数数量为208个 而K channel为16层 208/16=13 13-1=12（12为单个filter的参数数量）又因为原图是RGB3通道 所以filter的kernel_size=2(即该层有16个2*2*3channel的filter)
# 3. 同理，第二个卷积层有32个2*2*16channel的filter；第三个卷积层有64个2*2*32channel的filter。
# 4. 第三次卷积、最大池化、全局平均池化后，64个feature maps被平均化为64个值（每个feature map取一次平均）
# 5. GAP后得到的64个特征值，需要经过一个多分类器（这里选择softmax）转化为133个概率值，所以这里的参数数量为(64+1)*133=8645 其中1为bias
# 6. padding方式都选择valid
# 

# 概括下处理过程：
# 
# stride=1，kernel_size=2 x 2，填充方式为'valid'
# 
# 初始图片大小：224 x 224 x 3channel
# 
# 第一次卷积处理后：223 x 223 x 16channel，第一次最大池化后：111 x 111 x 16channel
# 
# 第二次卷积处理后：110 x 110 x 32channel，第二次最大池化后：55 x 55 x 32channel
# 
# 第三次卷积处理后：54 x 54 x 64channel，第三次最大池化后：27 x 27 x 64channel
# 
# 全局平均池化后：64个特征值
# 
# 最终分类：133类

# ---
# 
# <a id='question4'></a>  
# 
# ### __问题 4:__ 
# 
# 在下方的代码块中尝试使用 Keras 搭建卷积网络的架构，并回答相关的问题。
# 
# 1. 你可以尝试自己搭建一个卷积网络的模型，那么你需要回答你搭建卷积网络的具体步骤（用了哪些层）以及为什么这样搭建。
# 2. 你也可以根据上图提示的步骤搭建卷积网络，那么请说明为何如上的架构能够在该问题上取得很好的表现。
# 
# __回答:__ 
# 
# 本次答题先使用上图提示的步骤搭建CNN。因为上述架构:
# 1. 首先采用了多次CONV+POOL的策略对具体特征进行提取、抽象，并逐渐提取出一些抽象的“高级”特征
# 2. 在全连接层之前使用了GAP对参数量进行了缩减，可以加快模型训练速度，减少过拟合的可能性。
# 3. 参数数量处于可接受水平, 太多的参数一般机器可能跑不动。。。:-)

# #### 以下是一些答题过程中查阅的比较重要的文档

# #### Conv2D层

# 【以下网络上查到的资料出自http://keras-cn.readthedocs.io/en/latest/layers/convolutional_layer/ 】
# 
# keras.layers.convolutional.Conv2D(filters, kernel_size, strides=(1, 1), padding='valid', data_format=None, dilation_rate=(1, 1), activation=None, use_bias=True, kernel_initializer='glorot_uniform', bias_initializer='zeros', kernel_regularizer=None, bias_regularizer=None, activity_regularizer=None, kernel_constraint=None, bias_constraint=None)
# 二维卷积层，即对图像的空域卷积。该层对二维输入进行滑动窗卷积，当使用该层作为第一层时，应提供input_shape参数。例如input_shape = (128,128,3)代表128*128的彩色RGB图像（data_format='channels_last'）
# 
# 参数
# 
# filters：卷积核的数目（即输出的维度）
# kernel_size：单个整数或由两个整数构成的list/tuple，卷积核的宽度和长度。如为单个整数，则表示在各个空间维度的相同长度。
# strides：单个整数或由两个整数构成的list/tuple，为卷积的步长。如为单个整数，则表示在各个空间维度的相同步长。任何不为1的strides均与任何不为1的dilation_rate均不兼容
# padding：补0策略，为“valid”, “same” 。“valid”代表只进行有效的卷积，即对边界数据不处理。“same”代表保留边界处的卷积结果，通常会导致输出shape与输入shape相同。
# activation：激活函数，为预定义的激活函数名（参考激活函数），或逐元素（element-wise）的Theano函数。如果不指定该参数，将不会使用任何激活函数（即使用线性激活函数：a(x)=x）
# dilation_rate：单个整数或由两个个整数构成的list/tuple，指定dilated convolution中的膨胀比例。任何不为1的dilation_rate均与任何不为1的strides均不兼容。
# data_format：字符串，“channels_first”或“channels_last”之一，代表图像的通道维的位置。该参数是Keras 1.x中的image_dim_ordering，“channels_last”对应原本的“tf”，“channels_first”对应原本的“th”。以128x128的RGB图像为例，“channels_first”应将数据组织为（3,128,128），而“channels_last”应将数据组织为（128,128,3）。该参数的默认值是~/.keras/keras.json中设置的值，若从未设置过，则为“channels_last”。
# use_bias:布尔值，是否使用偏置项
# kernel_initializer：权值初始化方法，为预定义初始化方法名的字符串，或用于初始化权重的初始化器。参考initializers
# bias_initializer：权值初始化方法，为预定义初始化方法名的字符串，或用于初始化权重的初始化器。参考initializers
# kernel_regularizer：施加在权重上的正则项，为Regularizer对象
# bias_regularizer：施加在偏置向量上的正则项，为Regularizer对象
# activity_regularizer：施加在输出上的正则项，为Regularizer对象
# kernel_constraints：施加在权重上的约束项，为Constraints对象
# bias_constraints：施加在偏置上的约束项，为Constraints对象
# 输入shape
# 
# ‘channels_first’模式下，输入形如（samples,channels，rows，cols）的4D张量
# 
# ‘channels_last’模式下，输入形如（samples，rows，cols，channels）的4D张量
# 
# 注意这里的输入shape指的是函数内部实现的输入shape，而非函数接口应指定的input_shape，请参考下面提供的例子。
# 
# 输出shape
# 
# ‘channels_first’模式下，为形如（samples，nb_filter, new_rows, new_cols）的4D张量
# 
# ‘channels_last’模式下，为形如（samples，new_rows, new_cols，nb_filter）的4D张量
# 
# 输出的行列数可能会因为填充方法而改变

# #### MaxPooling2D层

# keras.layers.pooling.MaxPooling2D(pool_size=(2, 2), strides=None, padding='valid', data_format=None)
# 为空域信号施加最大值池化
# 
# 参数
# 
# pool_size：整数或长为2的整数tuple，代表在两个方向（竖直，水平）上的下采样因子，如取（2，2）将使图片在两个维度上均变为原长的一半。为整数意为各个维度值相同且为该数字。
# strides：整数或长为2的整数tuple，或者None，步长值。
# border_mode：‘valid’或者‘same’
# data_format：字符串，“channels_first”或“channels_last”之一，代表图像的通道维的位置。该参数是Keras 1.x中的image_dim_ordering，“channels_last”对应原本的“tf”，“channels_first”对应原本的“th”。以128x128的RGB图像为例，“channels_first”应将数据组织为（3,128,128），而“channels_last”应将数据组织为（128,128,3）。该参数的默认值是~/.keras/keras.json中设置的值，若从未设置过，则为“channels_last”。
# 输入shape
# 
# ‘channels_first’模式下，为形如（samples，channels, rows，cols）的4D张量
# 
# ‘channels_last’模式下，为形如（samples，rows, cols，channels）的4D张量
# 
# 输出shape
# 
# ‘channels_first’模式下，为形如（samples，channels, pooled_rows, pooled_cols）的4D张量
# 
# ‘channels_last’模式下，为形如（samples，pooled_rows, pooled_cols，channels）的4D张量

# #### 全局平均池化

# 以下资料引自：
# https://blog.csdn.net/jningwei/article/details/80064451
# https://blog.csdn.net/u014264373/article/details/80516294
# 
# “global pooling”就是pooling的 滑窗size 和整张feature map的size一样大。这样，每个 W×H×C
#  的feature map输入就会被转化为 1×1×C
#  输出。因此，其实也等同于每个位置权重都为 1/(W×H)
#  的FC层操作。
# 
# 等同于输入一个tensor，输出一根vector。
# 
# “global pooling”在滑窗内的具体pooling方法可以是任意的，所以就会被细分为“global avg pooling”、“global max pooling”等。
# 

# In[12]:


from keras.layers import Conv2D, MaxPooling2D, GlobalAveragePooling2D
from keras.layers import Dropout, Flatten, Dense
from keras.models import Sequential

model = Sequential()

### TODO: 定义你的网络架构
model.add(Conv2D(filters=16,kernel_size=2,padding='valid', activation='relu', input_shape=(224, 224, 3)))  
model.add(MaxPooling2D(pool_size=2))  
model.add(Conv2D(filters=32,kernel_size=2,padding='valid', activation='relu'))  
model.add(MaxPooling2D(pool_size=2))  
model.add(Conv2D(filters=64,kernel_size=2,padding='valid', activation='relu'))  
model.add(MaxPooling2D(pool_size=2))  
model.add(GlobalAveragePooling2D(input_shape=(27, 27, 64)))  
model.add(Dense(133, activation='softmax')) 
model.summary()


# In[13]:


## 编译模型
model.compile(optimizer='rmsprop', loss='categorical_crossentropy', metrics=['accuracy'])


# ---

# ## 【练习】训练模型
# 
# 
# ---
# 
# <a id='question5'></a>  
# 
# ### __问题 5:__ 
# 
# 在下方代码单元训练模型。使用模型检查点（model checkpointing）来储存具有最低验证集 loss 的模型。
# 
# 可选题：你也可以对训练集进行 [数据增强](https://blog.keras.io/building-powerful-image-classification-models-using-very-little-data.html)，来优化模型的表现。
# 
# 

# In[19]:


from keras.callbacks import ModelCheckpoint  

### TODO: 设置训练模型的epochs的数量

epochs = 2

### 不要修改下方代码

checkpointer = ModelCheckpoint(filepath='saved_models/weights.best.from_scratch.hdf5', 
                               verbose=1, save_best_only=True)

model.fit(train_tensors, train_targets, 
          validation_data=(valid_tensors, valid_targets),
          epochs=epochs, batch_size=20, callbacks=[checkpointer], verbose=1)


# In[20]:


## 加载具有最好验证loss的模型

model.load_weights('saved_models/weights.best.from_scratch.hdf5')


# ### 测试模型
# 
# 在狗图像的测试数据集上试用你的模型。确保测试准确率大于1%。

# In[21]:


# 获取测试数据集中每一个图像所预测的狗品种的index
dog_breed_predictions = [np.argmax(model.predict(np.expand_dims(tensor, axis=0))) for tensor in test_tensors]

# 报告测试准确率
test_accuracy = 100*np.sum(np.array(dog_breed_predictions)==np.argmax(test_targets, axis=1))/len(dog_breed_predictions)
print('Test accuracy: %.4f%%' % test_accuracy)


# ---
# <a id='step4'></a>
# ## 步骤 4: 使用一个CNN来区分狗的品种
# 
# 
# 使用 迁移学习（Transfer Learning）的方法，能帮助我们在不损失准确率的情况下大大减少训练时间。在以下步骤中，你可以尝试使用迁移学习来训练你自己的CNN。
# 

# ### 得到从图像中提取的特征向量（Bottleneck Features）

# In[22]:


bottleneck_features = np.load('bottleneck_features/DogVGG16Data.npz')
train_VGG16 = bottleneck_features['train']
valid_VGG16 = bottleneck_features['valid']
test_VGG16 = bottleneck_features['test']


# ### 模型架构
# 
# 该模型使用预训练的 VGG-16 模型作为固定的图像特征提取器，其中 VGG-16 最后一层卷积层的输出被直接输入到我们的模型。我们只需要添加一个全局平均池化层以及一个全连接层，其中全连接层使用 softmax 激活函数，对每一个狗的种类都包含一个节点。

# In[23]:


VGG16_model = Sequential()
VGG16_model.add(GlobalAveragePooling2D(input_shape=train_VGG16.shape[1:]))
VGG16_model.add(Dense(133, activation='softmax'))

VGG16_model.summary()


# In[24]:


## 编译模型

VGG16_model.compile(loss='categorical_crossentropy', optimizer='rmsprop', metrics=['accuracy'])


# In[25]:


## 训练模型

checkpointer = ModelCheckpoint(filepath='saved_models/weights.best.VGG16.hdf5', 
                               verbose=1, save_best_only=True)

VGG16_model.fit(train_VGG16, train_targets, 
          validation_data=(valid_VGG16, valid_targets),
          epochs=20, batch_size=20, callbacks=[checkpointer], verbose=1)


# In[26]:


## 加载具有最好验证loss的模型

VGG16_model.load_weights('saved_models/weights.best.VGG16.hdf5')


# ### 测试模型
# 现在，我们可以测试此CNN在狗图像测试数据集中识别品种的效果如何。我们在下方打印出测试准确率。

# In[27]:


# 获取测试数据集中每一个图像所预测的狗品种的index
VGG16_predictions = [np.argmax(VGG16_model.predict(np.expand_dims(feature, axis=0))) for feature in test_VGG16]

# 报告测试准确率
test_accuracy = 100*np.sum(np.array(VGG16_predictions)==np.argmax(test_targets, axis=1))/len(VGG16_predictions)
print('Test accuracy: %.4f%%' % test_accuracy)


# ### 使用模型预测狗的品种

# In[28]:


from extract_bottleneck_features import *

def VGG16_predict_breed(img_path):
    # 提取bottleneck特征
    bottleneck_feature = extract_VGG16(path_to_tensor(img_path))
    # 获取预测向量
    predicted_vector = VGG16_model.predict(bottleneck_feature)
    # 返回此模型预测的狗的品种
    return dog_names[np.argmax(predicted_vector)]


# In[29]:


VGG16_predict_breed(test_files[8])


# ---
# <a id='step5'></a>
# ## 步骤 5: 建立一个CNN来分类狗的品种（使用迁移学习）
# 
# 现在你将使用迁移学习来建立一个CNN，从而可以从图像中识别狗的品种。你的 CNN 在测试集上的准确率必须至少达到60%。
# 
# 在步骤4中，我们使用了迁移学习来创建一个使用基于 VGG-16 提取的特征向量来搭建一个 CNN。在本部分内容中，你必须使用另一个预训练模型来搭建一个 CNN。为了让这个任务更易实现，我们已经预先对目前 keras 中可用的几种网络进行了预训练：
# 
# - [VGG-19](https://s3.cn-north-1.amazonaws.com.cn/static-documents/nd101/DLND+documents/DogVGG19Data.npz) bottleneck features
# - [ResNet-50](https://s3.cn-north-1.amazonaws.com.cn/static-documents/nd101/DLND+documents/DogResnet50Data.npz) bottleneck features
# - [Inception](https://s3.cn-north-1.amazonaws.com.cn/static-documents/nd101/DLND+documents/DogInceptionV3Data.npz) bottleneck features
# - [Xception](https://s3.cn-north-1.amazonaws.com.cn/static-documents/nd101/DLND+documents/DogXceptionData.npz) bottleneck features
# 
# 这些文件被命名为为：
# 
#     Dog{network}Data.npz
# 
# 其中 `{network}` 可以是 `VGG19`、`Resnet50`、`InceptionV3` 或 `Xception` 中的一个。选择上方网络架构中的一个，下载相对应的bottleneck特征，并将所下载的文件保存在目录 `bottleneck_features/` 中。
# 
# 
# ### 【练习】获取模型的特征向量
# 
# 在下方代码块中，通过运行下方代码提取训练、测试与验证集相对应的bottleneck特征。
# 
#     bottleneck_features = np.load('bottleneck_features/Dog{network}Data.npz')
#     train_{network} = bottleneck_features['train']
#     valid_{network} = bottleneck_features['valid']
#     test_{network} = bottleneck_features['test']

# In[31]:


### TODO: 从另一个预训练的CNN获取bottleneck特征
bottleneck_features = np.load('bottleneck_features/DogVGG19Data.npz')
train_VGG19 = bottleneck_features['train']
valid_VGG19 = bottleneck_features['valid']
test_VGG19 = bottleneck_features['test']


# ### 【练习】模型架构
# 
# 建立一个CNN来分类狗品种。在你的代码单元块的最后，通过运行如下代码输出网络的结构：
#     
#         <your model's name>.summary()
#    
# ---
# 
# <a id='question6'></a>  
# 
# ### __问题 6:__ 
# 
# 
# 在下方的代码块中尝试使用 Keras 搭建最终的网络架构，并回答你实现最终 CNN 架构的步骤与每一步的作用，并描述你在迁移学习过程中，使用该网络架构的原因。
# 
# 
# __回答:__ 
# 
# 在步骤三、四中，我们只训练了2个epochs(尽管这两个epochs已经很耗时了)，也就是说我们自己搭建的网络架构并没有得到充分的训练(bottleneck layer中的参数并没有得到充分优化)，即该状况下bottleneck layer的输出是不可靠的。如果我们使用这样的输出去训练全连接层参数，也将得到不可靠的结果。此处我们可以使用迁移学习思想，将其他模型经过很长时间训练之后得到的bottleneck层输出数据(如VGG19或ResNet50的bottleneck数据)来训练全连接层的参数，以此来获得更加准确的预测结果。
# 
# 由于使用的是迁移学习，即 在全连接层的输入端引入其他训练好的模型bottleneck数据(如VGG19或ResNet50的bottleneck数据)以训练全连接层的参数。
# 
# 所以这里参考之前的架构，先用一个全局平均池化层对引入的bottleneck数据进行优化（对参数量进行缩减，加快模型训练速度，减少过拟合的可能性），然后继续使用softmax做全连接层的多分类器。
# 
# 具体代码实现如下：（尝试了VGG19和ResNet50两种预训练模型）

# 以下解释bottleneck层的示意图出自:
# https://blog.csdn.net/lovelyaiq/article/details/78690539
# 
# ![Sample Human Output](images/bottleneck_layer.png)
# 

# In[32]:


### TODO: 定义你的框架
VGG19_model = Sequential()  
VGG19_model.add(GlobalAveragePooling2D(input_shape=train_VGG19.shape[1:]))  
VGG19_model.add(Dense(133, activation='softmax'))  
  
VGG19_model.summary()  


# In[33]:


### TODO: 编译模型
VGG19_model.compile(loss='categorical_crossentropy', optimizer='rmsprop', metrics=['accuracy']) 


# ---
# 
# ### 【练习】训练模型
# 
# <a id='question7'></a>  
# 
# ### __问题 7:__ 
# 
# 在下方代码单元中训练你的模型。使用模型检查点（model checkpointing）来储存具有最低验证集 loss 的模型。
# 
# 当然，你也可以对训练集进行 [数据增强](https://blog.keras.io/building-powerful-image-classification-models-using-very-little-data.html) 以优化模型的表现，不过这不是必须的步骤。
# 

# In[45]:


### TODO: 训练模型
checkpointer = ModelCheckpoint(filepath='saved_models/weights.best.VGG19.hdf5', 
                               verbose=1, save_best_only=True)

VGG19_model.fit(train_VGG19, train_targets, 
          validation_data=(valid_VGG19, valid_targets),
          epochs=100, batch_size=20, callbacks=[checkpointer], verbose=1)


# 通过修改epochs的值【20，40，70，100】尝试取得更高的accuracy，最终在epochs=100时，accuracy超过了60%

# In[46]:


### TODO: 加载具有最佳验证loss的模型权重

VGG19_model.load_weights('saved_models/weights.best.VGG19.hdf5')


# ---
# 
# ### 【练习】测试模型
# 
# <a id='question8'></a>  
# 
# ### __问题 8:__ 
# 
# 在狗图像的测试数据集上试用你的模型。确保测试准确率大于60%。

# In[47]:


### TODO: 在测试集上计算分类准确率
VGG19_predictions = [np.argmax(VGG19_model.predict(np.expand_dims(feature, axis=0))) for feature in test_VGG19]  
  
# 报告测试准确率  
test_accuracy_VGG19 = 100 * np.sum(np.array(VGG19_predictions)==np.argmax(test_targets, axis=1))/len(VGG19_predictions)  
print('Test accuracy: %.4f%%' % test_accuracy_VGG19)  


# ### VGG19的accuracy不够理想，使用ResNet50再次进行尝试

# In[48]:


#使用ResNet50再次进行尝试
### TODO: 从另一个预训练的CNN获取bottleneck特征
bottleneck_features_Resnet50 = np.load('bottleneck_features/DogResnet50Data.npz')
train_Resnet50 = bottleneck_features_Resnet50['train']
valid_Resnet50 = bottleneck_features_Resnet50['valid']
test_Resnet50 = bottleneck_features_Resnet50['test']


# In[49]:


### TODO: 定义你的框架
Resnet50_model = Sequential()  
Resnet50_model.add(GlobalAveragePooling2D(input_shape=train_Resnet50.shape[1:]))  
Resnet50_model.add(Dense(133, activation='softmax'))  
  
Resnet50_model.summary() 


# In[50]:


### TODO: 编译模型
Resnet50_model.compile(loss='categorical_crossentropy', optimizer='rmsprop', metrics=['accuracy']) 


# In[57]:


### TODO: 训练模型
checkpointer_Resnet50 = ModelCheckpoint(filepath='saved_models/weights.best.Resnet50.hdf5', 
                               verbose=1, save_best_only=True)

Resnet50_model.fit(train_Resnet50, train_targets, 
          validation_data=(valid_Resnet50, valid_targets),
          epochs=20, batch_size=20, callbacks=[checkpointer_Resnet50], verbose=1)


# In[58]:


### TODO: 加载具有最佳验证loss的模型权重

Resnet50_model.load_weights('saved_models/weights.best.Resnet50.hdf5')


# In[59]:


### TODO: 在测试集上计算分类准确率
Resnet50_predictions = [np.argmax(Resnet50_model.predict(np.expand_dims(feature, axis=0))) for feature in test_Resnet50]  
  
# 报告测试准确率  
test_accuracy_Resnet50 = 100 * np.sum(np.array(Resnet50_predictions)==np.argmax(test_targets, axis=1))/len(Resnet50_predictions)  
print('Test accuracy: %.4f%%' % test_accuracy_Resnet50) 


# In[54]:


### TODO: 训练模型
checkpointer_Resnet50 = ModelCheckpoint(filepath='saved_models/weights.best.Resnet50.hdf5', 
                               verbose=1, save_best_only=True)

Resnet50_model.fit(train_Resnet50, train_targets, 
          validation_data=(valid_Resnet50, valid_targets),
          epochs=40, batch_size=20, callbacks=[checkpointer_Resnet50], verbose=1)


# In[55]:


### TODO: 加载具有最佳验证loss的模型权重

Resnet50_model.load_weights('saved_models/weights.best.Resnet50.hdf5')


# In[56]:


### TODO: 在测试集上计算分类准确率
Resnet50_predictions = [np.argmax(Resnet50_model.predict(np.expand_dims(feature, axis=0))) for feature in test_Resnet50]  
  
# 报告测试准确率  
test_accuracy_Resnet50 = 100 * np.sum(np.array(Resnet50_predictions)==np.argmax(test_targets, axis=1))/len(Resnet50_predictions)  
print('Test accuracy: %.4f%%' % test_accuracy_Resnet50) 


# 使用Resnet50的accuracy在epochs=20时为80%左右，在epochs=40时也在80%左右，表现比较稳定且可靠

# ---
# 
# ### 【练习】使用模型测试狗的品种
# 
# 
# 实现一个函数，它的输入为图像路径，功能为预测对应图像的类别，输出为你模型预测出的狗类别（`Affenpinscher`, `Afghan_hound` 等）。
# 
# 与步骤5中的模拟函数类似，你的函数应当包含如下三个步骤：
# 
# 1. 根据选定的模型载入图像特征（bottleneck features）
# 2. 将图像特征输输入到你的模型中，并返回预测向量。注意，在该向量上使用 argmax 函数可以返回狗种类的序号。
# 3. 使用在步骤0中定义的 `dog_names` 数组来返回对应的狗种类名称。
# 
# 提取图像特征过程中使用到的函数可以在 `extract_bottleneck_features.py` 中找到。同时，他们应已在之前的代码块中被导入。根据你选定的 CNN 网络，你可以使用 `extract_{network}` 函数来获得对应的图像特征，其中 `{network}` 代表 `VGG19`, `Resnet50`, `InceptionV3`, 或 `Xception` 中的一个。
#  
# ---
# 
# <a id='question9'></a>  
# 
# ### __问题 9:__

# #### 使用VGG19来进行分类

# In[60]:


from extract_bottleneck_features import *

def VGG19_predict_breed(img_path):
    # 提取bottleneck特征
    bottleneck_feature = extract_VGG19(path_to_tensor(img_path))
    # 获取预测向量
    predicted_vector = VGG19_model.predict(bottleneck_feature)
    # 返回此模型预测的狗的品种
    return dog_names[np.argmax(predicted_vector)]


# In[62]:


VGG19_predict_breed(test_files[8])


# In[63]:


VGG19_predict_breed(test_files[82])


# #### 使用ResNet50来进行分类

# In[61]:


from extract_bottleneck_features import *

def Resnet50_predict_breed(img_path):
    # 提取bottleneck特征
    bottleneck_feature = extract_Resnet50(path_to_tensor(img_path))
    # 获取预测向量
    predicted_vector = Resnet50_model.predict(bottleneck_feature)
    # 返回此模型预测的狗的品种
    return dog_names[np.argmax(predicted_vector)]


# In[64]:


Resnet50_predict_breed(test_files[8])


# In[65]:


Resnet50_predict_breed(test_files[82])


# ---
# 
# <a id='step6'></a>
# ## 步骤 6: 完成你的算法
# 
# 
# 
# 实现一个算法，它的输入为图像的路径，它能够区分图像是否包含一个人、狗或两者都不包含，然后：
# 
# - 如果从图像中检测到一只__狗__，返回被预测的品种。
# - 如果从图像中检测到__人__，返回最相像的狗品种。
# - 如果两者都不能在图像中检测到，输出错误提示。
# 
# 我们非常欢迎你来自己编写检测图像中人类与狗的函数，你可以随意地使用上方完成的 `face_detector` 和 `dog_detector` 函数。你__需要__在步骤5使用你的CNN来预测狗品种。
# 
# 下面提供了算法的示例输出，但你可以自由地设计自己的模型！
# 
# ![Sample Human Output](images/sample_human_output.png)
# 
# 
# 
# 
# <a id='question10'></a>  
# 
# ### __问题 10:__
# 
# 在下方代码块中完成你的代码。
# 
# ---
# 

# In[86]:


### TODO: 设计你的算法
### 自由地使用所需的代码单元数吧
def myFinalPredict(img_path):  
    image = cv2.imread(img_path)  
    # 将BGR图像转变为RGB图像以打印  
    cv_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB) 
    plt.imshow(cv_rgb)  
    plt.show()
    if dog_detector(img_path) == True:  
        print("Welcome Dog")  
        print("You are a 【{}】 ".format(Resnet50_predict_breed(img_path)))
    elif face_detector(img_path) > 0:  
        print("Welcome Human")  
        print("You look like a 【{}】 in dog world".format(Resnet50_predict_breed(img_path)))  
    else:  
        print("Something went wrong with your input")


# ---
# <a id='step7'></a>
# ## 步骤 7: 测试你的算法
# 
# 在这个部分中，你将尝试一下你的新算法！算法认为__你__看起来像什么类型的狗？如果你有一只狗，它可以准确地预测你的狗的品种吗？如果你有一只猫，它会将你的猫误判为一只狗吗？
# 
# 
# <a id='question11'></a>  
# 
# ### __问题 11:__
# 
# 在下方编写代码，用至少6张现实中的图片来测试你的算法。你可以使用任意照片，不过请至少使用两张人类图片（要征得当事人同意哦）和两张狗的图片。
# 同时请回答如下问题：
# 
# 1. 输出结果比你预想的要好吗 :) ？或者更糟 :( ？
# 2. 提出至少三点改进你的模型的想法。

# 回答：
# 
# 1. 输出结果比预想要好，只有一张图片中的狗被错误分类为“人”，而狗狗分类算法部分基本准确。<br>
# 2. 关于改进模型的建议：<br>
#    a. 提高face_detector(img_path)部分的准确率，以尽量避免出现狗被识别成人的概率。<br>
#    b. 因为狗狗分类算法的accuracy目前只有80%左右(ResNet50)，可以尝试一些提高准确率的方案。（如将GAP层替换成其他类型的全局池化层（例如全局最大池化等），又如可以尝试Inception或者Xception）<br>
#    c. 在模型学习过程中，尝试不同的epochs，以获得较高的准确率。<br>

# In[87]:


## TODO: 在你的电脑上，在步骤6中，至少在6张图片上运行你的算法。
## 自由地使用所需的代码单元数吧
myFinalPredict("my_test_img/dogs/corgi.jpg")


# 识别准确

# In[89]:


myFinalPredict("my_test_img/dogs/pinscher.jpg")


# 识别准确

# In[90]:


myFinalPredict("my_test_img/dogs/shepherd.jpg")


# 识别错误<br>
# 图片应为:German_shepherd_dog
# 被识别成为:像German_shepherd_dog狗的人

# In[88]:


myFinalPredict("my_test_img/dogs/Tibetan.jpg")


# 识别准确

# In[91]:


myFinalPredict("my_test_img/dogs/juxingguibin.jpg")


# 识别准确

# In[93]:


myFinalPredict("my_test_img/dogs/human1.jpg")


# 识别准确

# In[94]:


myFinalPredict("my_test_img/dogs/human2.jpg")


# 识别准确

# **注意: 当你写完了所有的代码，并且回答了所有的问题。你就可以把你的 iPython Notebook 导出成 HTML 文件。你可以在菜单栏，这样导出File -> Download as -> HTML (.html)把这个 HTML 和这个 iPython notebook 一起做为你的作业提交。**
