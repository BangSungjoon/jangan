FROM node:22-alpine

WORKDIR /app

# 시스템 패키지 업데이트 및 ffmpeg 설치
# RUN apt-get update && apt-get install -y ffmpeg
RUN apk add --no-cache ffmpeg

COPY package*.json ./
RUN npm install

# 로컬 폴더의 파일을 컨테이너로 복사
COPY . /app

# mpeg1muxer.js 파일을 커스텀 파일로 교체
COPY custom_mpeg1muxer.js node_modules/node-rtsp-stream/mpeg1muxer.js

EXPOSE 3000 10000-10100

# 앱 실행 명령어
CMD ["npm", "start"]