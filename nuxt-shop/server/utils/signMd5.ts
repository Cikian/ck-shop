import CryptoJS from 'crypto-js'

export function signMd5Utils(config: { signSecret: string }) {
  const signatureSecret = config.signSecret

  function sortAsc(jsonObj: Record<string, any>): Record<string, any> {
    const arr = Object.keys(jsonObj)
    const sortArr = arr.sort()
    const sortObj: Record<string, any> = {}
    for (const key of sortArr) {
      sortObj[key] = jsonObj[key]
    }
    return sortObj
  }

  function parseQueryString(url: string): Record<string, any> {
    const result: Record<string, any> = {}
    const urlReg = /^[^\?]+\?([\w\W]+)$/
    const paramReg = /([^&=]+)=([\w\W]*?)(&|$|#)/g

    const lastpathVariable = url.substring(url.lastIndexOf('/') + 1)
    if (lastpathVariable.includes(',')) {
      let cleanVariable = lastpathVariable
      if (cleanVariable.includes('?')) {
        cleanVariable = cleanVariable.substring(0, cleanVariable.indexOf('?'))
      }
      result['x-path-variable'] = decodeURIComponent(cleanVariable)
    }

    const urlArray = urlReg.exec(url)
    if (urlArray && urlArray[1]) {
      const paramString = urlArray[1]
      let paramResult
      while ((paramResult = paramReg.exec(paramString)) !== null) {
        if (typeof paramResult[2] === 'number' && !isNaN(paramResult[2])) {
          paramResult[2] = paramResult[2].toString()
        }
        result[paramResult[1]] = paramResult[2]
      }
    }

    return result
  }

  function mergeObject(objectOne: Record<string, any>, objectTwo?: Record<string, any>): Record<string, any> {
    if (!objectTwo || Object.keys(objectTwo).length === 0) {
      return objectOne
    }
    for (const key in objectTwo) {
      if (Object.prototype.hasOwnProperty.call(objectTwo, key)) {
        let value = objectTwo[key]
        if (typeof value === 'number' && !isNaN(value)) {
          value = value.toString()
        }
        if (typeof value === 'boolean') {
          value = value.toString()
        }
        objectOne[key] = value
      }
    }
    return objectOne
  }

  function getSign(url: string, requestParams?: Record<string, any>, requestBodyParams?: Record<string, any>): string {
    let urlParams = parseQueryString(url)
    let jsonObj = mergeObject(urlParams, requestParams)
    if (requestBodyParams) {
      jsonObj = mergeObject(jsonObj, requestBodyParams)
    }
    const requestBody = sortAsc(jsonObj)
    delete requestBody._t
    return CryptoJS.MD5(JSON.stringify(requestBody) + signatureSecret).toString().toUpperCase()
  }

  function getTimestamp(): number {
    return Date.now()
  }

  return {
    getSign,
    getTimestamp,
  }
}