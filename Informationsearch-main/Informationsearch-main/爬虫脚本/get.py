import requests
import json
import time
# 假设你的JSON数据存储在data.json文件中
json_file = '爬虫.json'

# 读取JSON文件
with open(json_file, 'r', encoding='utf-8') as file:
    data = json.load(file)

# 遍历JSON数据
for category, items in data.items():
    for item in items:
        # 拼接URL
        url = f'http://localhost:8080/parse/{category}:{item}'
        try:
            # 发送GET请求
            response = requests.get(url)
            # 打印响应状态码和内容
            print(f'访问 {url} 的响应状态码: {response.status_code}, 响应内容: {response.text}')
        except requests.exceptions.RequestException as e:
            # 打印错误信息
            print(f'访问 {url} 时发生错误: {e}')
            time.sleep(0.1)