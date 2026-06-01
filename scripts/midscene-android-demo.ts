import 'dotenv/config';
import {
  AndroidAgent,
  AndroidDevice,
  getConnectedDevices,
} from '@midscene/android';

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function main() {
  const devices = await getConnectedDevices();
  if (!devices.length) {
    throw new Error('No Android device found. Please start an emulator or connect a phone with adb.');
  }

  const device = new AndroidDevice(devices[0].udid);
  const agent = new AndroidAgent(device, {
    aiActionContext:
      'The target app is an audio copywriting generator. If a permission, agreement, or popup appears, accept it. Do not log in to anything.',
  });

  await device.connect();

  await agent.runAdbShell(
    'am start -n com.example.mediaagent/.MainActivity',
  );
  await sleep(1500);

  await agent.aiAssert('The page title is 音频文案生成器');
  await agent.aiAction('Tap the 示例演示 button');
  await agent.aiWaitFor('The page shows 解析与文案');
  await agent.aiAssert('The result contains 朋友圈文案 or 小红书文案');

  console.log('Midscene Android demo check passed.');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
