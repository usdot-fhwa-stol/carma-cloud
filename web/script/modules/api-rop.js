
import {delay, buildRequestData, promiseData, REQUEST_JSON} from './util.js';
const RULE_USED = 'I';
const RULE_UNUSED = 'U';
const RULE_ARCHIVED = 'A';
const RULE_DELETED = 'D';

const ROP_DATA = '{"Ye-Zt_XPQrO-9v8PEdclHg":{"name":"A rule","rules":[{"tol":"3","val":"2","obstype":"SPDWND","operator":"eq","type":"any"},{"tol":"5","val":"1","obstype":"SPDWND","operator":"lt","type":"mean"}],"controls":[{"type":"ADS","inputs":{}},{"type":"PLTN_CNT","inputs":{"v_c":"2"}},{"type":"VEHICL_PROP","inputs":{"m_w":"1","m_h":"2","m_l":"3"}},{"type":"VEHICL_TYPE","inputs":{"v_t":"c"}}],"status":"U"},"qqxgtzSYRTqCkfkY9toCcA":{"id":0.6810953762130116,"name":"A rule","rules":[{"tol":"3","val":"2","obstype":"SPDWND","operator":"eq","type":"any"},{"tol":"5","val":"1","obstype":"SPDWND","operator":"lt","type":"mean"}],"controls":[{"type":"ADS","inputs":{}},{"type":"PLTN_CNT","inputs":{"v_c":"2"}},{"type":"VEHICL_PROP","inputs":{"m_w":"1","m_h":"2","m_l":"3"}},{"type":"VEHICL_TYPE","inputs":{"v_t":"c"}}],"status":"U"},"uV0V-6YLQCGRF_Ty4cBKuQ":{"rules":[{"tol":"3","val":"2","obstype":"SPDWND","operator":"eq","type":"any"},{"tol":"5","val":"1","obstype":"SPDWND","operator":"lt","type":"mean"}],"name":"Copied rule","controls":[{"type":"ADS","inputs":{}},{"type":"PLTN_CNT","inputs":{"v_c":"2"}},{"type":"VEHICL_PROP","inputs":{"m_w":"1","m_h":"2","m_l":"3"}},{"type":"VEHICL_TYPE","inputs":{"v_t":"c"}}],"status":"U"}}';

let loadedRules;

const loadRules = () => $.post("api/rop", buildRequestData(), null, 'json')
//promiseData(JSON.parse(sessionStorage.getItem('ROP_DATA') ? sessionStorage.getItem('ROP_DATA') : ROP_DATA)).then(delay(1500))
    .then(respData => {
      const ropArray = [];
      for (let [key, value] of Object.entries(respData))
      {
        value.id = key;
        ropArray.push(value);
      }
      loadedRules = respData;
      return ropArray;
    });

const loadRule = ropId => {
  if (loadedRules)
    return promiseData(loadedRules[ropId]);
  else
    return loadRules().then(data => promiseData(loadedRules[ropId]));
};

const saveRule = (ropData) => {
  return  $.post("api/rop/save", buildRequestData(ropData, {type: REQUEST_JSON}), null, 'json')
    .promise()
    .then(data =>
    {
      const newId = Object.keys(data)[0];
      const savedRop = data[newId];
      savedRop.id = newId;
      if (loadedRules)
      {
        loadedRules[newId] = savedRop;
        sessionStorage.setItem('ROP_DATA', JSON.stringify(loadedRules));
      }
      return savedRop;
    });
};

const updateRuleStatus = (id, status) => {
  loadRule(id)
    .then(ruleData => {
      ruleData.status = status;
      saveRule(ruleData);
      delete loadedRules[id];
    });
};

export {loadRules, loadRule, saveRule, updateRuleStatus, RULE_UNUSED, RULE_ARCHIVED, RULE_USED, RULE_DELETED};