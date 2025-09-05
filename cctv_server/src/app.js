const express = require('express');
const routes = require('./routes/index');
const frameService = require('./services/frameService');
const streamService = require('./services/streamService');
const apiService = require('./services/apiService');
const { initializeDb } = require('./config/database');
require('dotenv').config();
const cors = require('cors');

const app = express();
app.use(cors({
  origin: ['http://localhost:5174', 'http://152.42.220.24'],
  methods: ['GET'],
}));
app.use(express.json());
app.use('/api', routes);

(async () => {
  await initializeDb(); // DB 초기화
  const stationId = process.env.STATION_ID;
  // const cctvData = await apiService.fetchCctvData(stationId); // CCTV 정보 가져오기
  // const cctvList = cctvData.result

  const cctvList = [
      { beacon_code: 1001, rtsp_url: "rtsp://172.20.0.1:554/cctv"},
  //     { beacon_code: 1002, rtsp_url: "rtsp://localhost/cctv"},
  //     { beacon_code: 1003, rtsp_url: "rtsp://localhost/cctv"},
  ]

  frameService.initializeFrameExtraction(stationId, cctvList);
  streamService.startStreams(stationId, cctvList);
})();

const PORT = process.env.PORT;
app.listen(PORT,
  () => console.log(`서버 시작, 포트 번호: ${PORT}`));

process.on('SIGINT', () => {
  process.exit();
});