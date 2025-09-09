const Stream = require('node-rtsp-stream');
const { storeCctvData } = require('../config/database');

const usedPorts = new Set();
const BASE_WS_PORT = 10000;
const activeStreams = new Map();

const getAvailablePort = () => {
  let port = BASE_WS_PORT;
  while (usedPorts.has(port)) port++;
  usedPorts.add(port);
  return port;
};

const startStreams = async (stationId, cctvList) => {
  await Promise.all(
    cctvList.map(async (cctv) => {
      const { rtsp_url, beacon_code } = cctv;
      const wsPort = getAvailablePort();

      if (activeStreams.has(beacon_code)) {
        console.log(`이미 실행 중: CCTV ${beacon_code}`);
        return;
      }

      const stream = new Stream({
        name: `rtsp_stream_${stationId}_${wsPort}`,
        streamUrl: rtsp_url,
        wsPort: wsPort,
        host: '0.0.0.0' // 외부 접근 가능하게 바인딩
      });

      stream.wsServer.on('connection', () => {
        console.log(`웹 소켓 연결됨: ws://${process.env.SERVER_IP}:${wsPort}`);
        activeStreams.set(beacon_code, stream); // 활성 스트림에 추가
      });

      stream.on('error', (err) => {
        console.error(`스트리밍 오류 (CCTV ${beacon_code}): ${err.message}`);
        activeStreams.delete(beacon_code);
        usedPorts.delete(wsPort);
      });

      try {
        const wsUrl = `ws://${process.env.SERVER_IP}:${wsPort}`;
        await storeCctvData(stationId, beacon_code, wsUrl);
        console.log(`DB에 저장된 웹소켓 주소: ${wsUrl}`);
      } catch (err) {
        console.error(`DB 저장 실패 (CCTV ${beacon_code}): ${err.message}`);
      }
    })
  );
};

module.exports = { startStreams };