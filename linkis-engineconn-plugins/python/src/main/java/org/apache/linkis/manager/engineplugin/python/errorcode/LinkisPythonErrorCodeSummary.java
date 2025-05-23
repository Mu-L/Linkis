/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.linkis.manager.engineplugin.python.errorcode;

import org.apache.linkis.common.errorcode.LinkisErrorCode;

public enum LinkisPythonErrorCodeSummary implements LinkisErrorCode {
  PYTHON_EXECUTE_ERROR(60002, ""),
  PYSPARK_PROCESSS_STOPPED(60003, "python process has stopped, query failed!(Python 进程已停止，查询失败！)"),
  INVALID_PYTHON_SESSION(400201, "Invalid python session.(无效的 python 会话.)");
  /** 错误码 */
  private final int errorCode;
  /** 错误描述 */
  private final String errorDesc;

  LinkisPythonErrorCodeSummary(int errorCode, String errorDesc) {
    this.errorCode = errorCode;
    this.errorDesc = errorDesc;
  }

  @Override
  public int getErrorCode() {
    return errorCode;
  }

  @Override
  public String getErrorDesc() {
    return errorDesc;
  }
}
