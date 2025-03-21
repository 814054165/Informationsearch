import requests
import json
import time
import os
import random
import platform

# 假设你的JSON数据存储在data.json文件中
current_script_path = os.path.abspath(__file__)
current_script_dir = os.path.dirname(current_script_path)

# 构造相对于当前脚本文件的 `爬虫.json` 文件路径
json_file = os.path.join(current_script_dir, '爬虫.json')

# 读取JSON文件
with open(json_file, 'r', encoding='utf-8') as file:
    data = json.load(file)

# 将所有类别和项目组合成一个列表
all_items = [(category, item) for category, items in data.items() for item in items]

# 打乱顺序
random.shuffle(all_items)

# 总项目数
total_items = len(all_items)

# 定义清空命令行的函数
def clear_console():
    # 根据操作系统类型选择清空命令
    os.system('cls' if platform.system() == 'Windows' else 'clear')

# 定义打印条状进度条的函数
def print_progress_bar(index, total, bar_length=50):
    # 计算进度百分比
    progress = (index + 1) / total
    # 计算已完成部分的长度
    completed_length = int(bar_length * progress)
    # 构造进度条
    bar = "#" * completed_length + "-" * (bar_length - completed_length)
    # 打印进度条
    print(f"进度: [{bar}] {index + 1}/{total} ({progress * 100:.2f}%)")

# 遍历所有打乱后的类别和项目
for index, (category, item) in enumerate(all_items):
    # 拼接URL
    url = f'http://localhost:8080/parse/{category}:{item}'
    try:
        # 发送GET请求
        response = requests.get(url)
        # 打印响应状态码和内容
        # 清空命令行
        clear_console()
        print(f'访问 {url} 的响应状态码: {response.status_code}, 响应内容: {response.text}')
    except requests.exceptions.RequestException as e:
        # 打印错误信息
        print(f'访问 {url} 时发生错误: {e}')
    
    
    # 打印条状进度条
    print_progress_bar(index, total_items)
    
    time.sleep(0.01)  # 添加适当的延迟以避免过快的请求

print("所有请求已完成！")