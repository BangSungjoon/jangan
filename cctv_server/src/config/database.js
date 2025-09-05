
const redis = require('redis');
const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';
const client = redis.createClient({ url: redisUrl });

client.connect();

// CCTV 데이터 저장: key를 station_id:beacon_code, value를 ws_url로 저장
const storeCctvData = async (station_id, beacon_code, ws_url) => {
  const key = `cctv_info:${station_id}:${beacon_code}`;
  await client.set(key, ws_url);
};

// CCTV 데이터 조회
const getCctvData = async (station_id, beacon_code) => {
  const key = `cctv_info:${station_id}:${beacon_code}`;
  const ws_url = await client.get(key);
  return ws_url;
};

module.exports = { storeCctvData, getCctvData };