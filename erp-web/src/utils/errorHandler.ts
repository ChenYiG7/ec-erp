import { ElNotification } from 'element-plus'

/**
 * @description 全局代码错误捕捉
 * */
const errorHandler = (error: any) => {
  // 过滤 HTTP 请求错误(带 status):响应拦截器已按状态码统一弹提示
  if (error.status || error.status == 0) {
    return false
  }
  // 非真实 JS 异常静默(#26 二轮走查):ElMessageBox 取消以 'cancel'/'close' 字符串 reject(用户主动取消非异常);
  // 业务错误以 Result 对象 reject(拦截器已弹提示)。二者经 Vue 异步事件链路路由到本 errorHandler,
  // 此前会误弹「未知错误 cancel」通知
  if (!(error instanceof Error)) {
    return false
  }
  const errorMap: { [key: string]: string } = {
    InternalError: 'Javascript引擎内部错误',
    ReferenceError: '未找到对象',
    TypeError: '使用了错误的类型或对象',
    RangeError: '使用内置对象时，参数超范围',
    SyntaxError: '语法错误',
    EvalError: '错误的使用了Eval',
    URIError: 'URI错误',
  }
  console.error(error)
  const errorName = errorMap[error.name] || '未知错误'
  ElNotification({
    title: errorName,
    message: error.message,
    type: 'error',
    duration: 3000,
  })
}

export default errorHandler
