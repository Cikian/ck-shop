// composables/useCaptcha.ts

export const useCaptcha = () => {
  const imageBase64 = useState<string>('captcha-image', () => '')
  const checkKey = useState<string>('captcha-check-key', () => '')

  // 对应参考代码里的：
  // randCodeData.checkKey = new Date().getTime() + Math.random().toString(36).slice(-4)
  const generateCheckKey = () => {
    return Date.now() + Math.random().toString(36).slice(-4)
  }

  const refreshCaptcha = async () => {
    checkKey.value = generateCheckKey()
    const data = await $fetch<{ image: string }>(`/api/captcha/${checkKey.value}`)
    imageBase64.value = data.image
  }

  return { imageBase64, checkKey, refreshCaptcha }
}
