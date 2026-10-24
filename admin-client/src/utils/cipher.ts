import { encrypt, decrypt } from 'crypto-js/aes';
import { parse } from 'crypto-js/enc-utf8';
import pkcs7 from 'crypto-js/pad-pkcs7';
import ECB from 'crypto-js/mode-ecb';
import md5 from 'crypto-js/md5';
import UTF8 from 'crypto-js/enc-utf8';
import Base64 from 'crypto-js/enc-base64';
import CryptoJS from 'crypto-js';

export interface EncryptionParams {
  key: string;
  iv: string;
}

export class AesEncryption {
  private key;
  private iv;

  constructor(opt: Partial<EncryptionParams> = {}) {
    const { key, iv } = opt;
    if (key) {
      this.key = parse(key);
    }
    if (iv) {
      this.iv = parse(iv);
    }
  }

  get getOptions() {
    return {
      mode: ECB,
      padding: pkcs7,
      iv: this.iv,
    };
  }

  encryptByAES(cipherText: string) {
    return encrypt(cipherText, this.key, this.getOptions).toString();
  }

  decryptByAES(cipherText: string) {
    return decrypt(cipherText, this.key, this.getOptions).toString(UTF8);
  }
}

export function encryptByBase64(cipherText: string) {
  return UTF8.parse(cipherText).toString(Base64);
}

export function decodeByBase64(cipherText: string) {
  return Base64.parse(cipherText).toString(UTF8);
}

export function encryptByMd5(password: string) {
  return md5(password).toString();
}

// ================== 密码加密相关 ==================
// 密码加密统一使用 AES CBC 模式，前后端 key 和 iv 必须保持一致
// AES_KEY 和 AES_IV 需与后端配置完全一致，否则加密/解密会失败
// ================== 密码加密相关 ===========BEGIN=======

// AES加密key和iv常量

/**
 * 后端已把传输口令密钥轮换为私有值（清单 B17）：ck-server/.env 的
 * JEECG_AES_KEY / JEECG_AES_IV（EncryptedString 启动时覆盖静态字段）。
 * 前端**不能**再写死开源默认值，否则后端解出来的口令是乱码，
 * 表现就是"账号密码都对，却一直提示用户名或密码错误"（日志里也没有线索）。
 *
 * 取值：admin-client/.env（gitignored）的 VITE_AES_KEY / VITE_AES_IV；
 * 未配置时才回退 JeecgBoot 开源默认值（仅适用于尚未轮换密钥的老环境）。
 */
const OPEN_SOURCE_DEFAULT_KEY = '1234567890adbcde';
const OPEN_SOURCE_DEFAULT_IV = '1234567890hjlkew';

function readEnv(name: string): string {
  const value = (import.meta.env as unknown as Record<string, unknown>)[name];
  return typeof value === 'string' ? value.trim() : '';
}

export const AES_KEY = readEnv('VITE_AES_KEY') || OPEN_SOURCE_DEFAULT_KEY;
export const AES_IV = readEnv('VITE_AES_IV') || OPEN_SOURCE_DEFAULT_IV;

if (import.meta.env.DEV && AES_KEY === OPEN_SOURCE_DEFAULT_KEY) {
  console.warn(
    '[cipher] 未配置 VITE_AES_KEY / VITE_AES_IV，正在使用 JeecgBoot 开源默认密钥；' +
      '若后端已轮换 JEECG_AES_KEY/JEECG_AES_IV，登录会提示“用户名或密码错误”。',
  );
}

/**
 * AES CBC 加密，使用全局常量 AES_KEY 和 AES_IV
 * @param plainText 明文
 * @returns 加密后的密文
 */
export function encryptAESCBC(plainText: string): string {
  const key = parse(AES_KEY);
  const iv = parse(AES_IV);
  return encrypt(plainText, key, {
    iv: iv,
    mode: CryptoJS.mode.CBC,
    padding: pkcs7
  }).toString();
}
// ================== 密码加密相关 =============END=====
