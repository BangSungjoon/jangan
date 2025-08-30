const ffmpeg = require('fluent-ffmpeg');
const fs = require('fs');
const path = require('path');
const axios = require('axios');
const FormData = require('form-data');

// 파일 저장 경로: src/frames -> 없으면 생성
const FRAME_DIR = path.join(__dirname, '../frames');
if (!fs.existsSync(FRAME_DIR)) fs.mkdirSync(FRAME_DIR);

// 프레임 추출 성공 여부 체크
const extractStatus = new Map();

// 파일 생성 대기
const waitForFile = (filePath, timeout = 3000) => {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const interval = setInterval(() => {
      if (fs.existsSync(filePath) && fs.statSync(filePath).size > 0) {
        clearInterval(interval);
        resolve(true);
      } else if (Date.now() - start > timeout) {
        clearInterval(interval);
        reject(new Error(`파일 생성 타임아웃: ${filePath}`));
      }
    }, 300);
  });
};

// 프레임 추출
const startFrameExtraction = (rtspUrl, beaconCode) => {
  const outputFile = path.join(FRAME_DIR, `${beaconCode}.jpg`);
const proc = ffmpeg(rtspUrl)
    .inputOptions([
      '-rtsp_transport', 'tcp',
      '-probesize', '2000000',
      '-analyzeduration', '1000000',
      '-fflags', '+nobuffer',
      '-err_detect', 'ignore_err',
    ])
    .videoFilter('fps=1,scale=-1:-1')
    .outputOptions([
      '-q:v', '2',
      '-update', '1',
      '-preset', 'ultrafast',
      '-fps_mode', 'cfr',
      '-pix_fmt', 'yuvj420p',
    ])
    .outputFormat('image2')
    .output(outputFile)
    .on('start', () => console.log(`프레임 추출 시작: CCTV ${beaconCode}`))
    .on('end', () => {
      console.log(`프레임 추출 종료 (CCTV ${beaconCode})`);
      extractStatus.set(beaconCode, true); // 성공 시 체크
    })
    .on('error', (err) => {
      console.error(`프레임 추출 오류 (CCTV ${beaconCode}): ${err.message}`);
      extractStatus.delete(beaconCode); // 오류 발생 시 목록에서 제거
    })
    .on('stderr', (data) => console.error(`FFmpeg 로그 (CCTV ${beaconCode}): ${data}`));

  proc.run();
};

// 프레임 전송
const sendFrames = async (stationId, cctvList) => {
  const fast_api_url = process.env.FAST_API_URL;

  // FormData 생성
  const form = new FormData();
  form.append('station_id', stationId.toString());  // station_id는 문자열로 변환
  const cctvListStringified = cctvList.map(cctv => ({
    ...cctv,
    beacon_code: cctv.beacon_code.toString(),       // beacon_code도 문자열로 변환
  }));
  form.append('cctv_list', JSON.stringify(cctvListStringified));

  // FormData에 파일 추가
  const filePromises = cctvList.map(async ({ beacon_code }) => {
    const filePath = path.join(FRAME_DIR, `${beacon_code}.jpg`);
    try {
      await waitForFile(filePath); // 파일 생성 대기
      const frameStream = fs.createReadStream(filePath);
      form.append('files', frameStream, `${beacon_code}.jpg`);
      console.log(`프레임 추가: CCTV ${beacon_code}`);
    } catch (error) {
      console.warn(`프레임 파일 없음: CCTV ${beacon_code}`);
    }
  });

  await Promise.all(filePromises);

  try {
    const response = await axios.post(`${fast_api_url}/ai/cctv-frame`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    console.log('프레임 전송 완료', response.status);
  } catch (error) {
    console.error('전송 에러:', error.message, error.response?.data || '');
  }
};

// 프레임 추출 시작
const initializeFrameExtraction = (stationId, cctvList) => {
  cctvList.forEach(({ rtsp_url, beacon_code }) => {
    extractStatus.set(beacon_code, false); // 추출 시작 전 각 CCTV의 상태 초기화
    startFrameExtraction(rtsp_url, beacon_code);
  });

  setInterval(async () => {
    const allReady = cctvList.every(({ beacon_code }) => {
      const filePath = path.join(FRAME_DIR, `${beacon_code}.jpg`);
      return fs.existsSync(filePath) && fs.statSync(filePath).size > 0;
    });

    if (allReady) {
      await sendFrames(stationId, cctvList);
    }
  }, 3000);
};

module.exports = {
  initializeFrameExtraction
};