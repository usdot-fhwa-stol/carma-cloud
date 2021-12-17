import {delay, entriesToOptions, objectToSelectElementHtml, promiseData} from './modules/util.js';
import {loadRules, loadRule, saveRule, updateRuleStatus,
  RULE_UNUSED, RULE_ARCHIVED, RULE_USED, RULE_DELETED} from './modules/api-rop.js';


const INPUT_LIST = 'LIST';
const INPUT_INT = 'INTEGER';
const INPUT_TEXT = 'TEXT';
const INPUT_DECIMAL = 'DECIMAL';

const inputProducers = new Map([
  [INPUT_INT, def => `<input required pattern="\d*\.?\d*" name="${def.id}" type="text" size="5" />`],
  [INPUT_LIST, def => objectToSelectElementHtml(def.options, {name: def.id, required: 'required'})]
]);

const buildControlInputBlock = input =>
  {
    let spanContent = [inputProducers.get(input.type)(input), input.label];
    //label on left for lists, otherwise on the right
    if (input.type === INPUT_LIST)
      spanContent = spanContent.reverse();

    return `
    <div><label>
      <span>${spanContent[0]}</span>
      <span>${spanContent[1]}</span>
    </label></div>`;
  };


const singleIntInput = (id, label) => [{type: INPUT_INT, label: label, id: id}];
const ftInput = id => singleIntInput(id, 'ft');
const controlDefMap = new Map([
  ['ADS', {label: 'ADS Not Allowed'}],
  ['PLTN_CNT', {label: 'Platoon Length', inputs: singleIntInput('v_c', 'Vehicles')}],
  ['PLTN_SEP', {label: 'Platoon Separation', inputs: ftInput('sp_ft')}],
  ['SPEED', {label: 'Maximum Speed', inputs: singleIntInput('spd', 'mph')}],
  ['HEADWAY', {label: 'Headway', inputs: ftInput('h_ft')}],
  ['LTRL_CNTRL', {label: 'No Lane Change'}],
  ['LN_OPEN_CLOSED', {label: 'Lanes Closed'}],
  ['VEH_HEIGHT', {label: 'Vehicle Height', inputs: ftInput('m_h')}],
  ['VEH_WIDTH', {label: 'Vehicle Width', inputs: ftInput('m_w')}],
  ['VEH_LENGTH', {label: 'Vehicle Length', inputs: ftInput('m_l')}],
  ['VEHICL_TYPE', {label: 'Vehicle Type', inputs: [{type: INPUT_LIST, label: 'Type', id: 'v_t', options: {tr: 'Commercial', c: 'Passenger'}}]}],
  ['VEHCL_OCCUPANCY', {label: 'Vehicle Occupancy', inputs: [{type: INPUT_INT, id: 'v_cnt', label: 'Passengers'}]}]
]);


const loadRuleRow = ({target}, copying) => {
  const ropTr = $(target).parents('tr');
  const ropId = ropTr.data('rop-id');
  $('#rop-name')
    .val(ropTr.data('rop-name'))
    .data('rop-id', copying ? null : ropId);

  const ruleDefTable = $('#table-rule tbody')
    .empty()
    .append('<tr><td colspan="4"><div class="loading-spinner" style="text-align:center;padding:20px;"><i class="fas fa-spinner fa-5x fa-spin"></i></div></td></tr>');



  const controlDefTable = $('#table-control tbody')
    .empty()
    .append('<tr><td colspan="2"><div class="loading-spinner" style="text-align:center;padding:20px;"><i class="fas fa-spinner fa-5x fa-spin"></i></div></td></tr>');



  loadRule(ropId).then(ruleData => {
    ruleDefTable.empty();
    controlDefTable.empty();

    if (ruleData.rules)
    {
      for (let rule of ruleData.rules)
      {
        const newRow = $(buildRuleRow(rule));
        newRow.data('rop', rule);
        ruleDefTable.append(newRow).find('.rop-delete').click(deleteParentRow);
      }
    }

    if (ruleData.controls)
    {
      ruleData.controls.forEach(control => {
        const newRow = $(buildControlRow(control, controlDefMap));
        newRow.data('control', control);
        controlDefTable.append(newRow).find('.rop-delet').click(deleteParentRow);
      });
    }
  });
};

const deleteParentRow = ({target}) => $(target).parents('tr').remove();
const existingRulesPromise = loadRules();

const rebuildRowIn = (target, newStatus, newRowSelector) =>
  $('.existing-rop-container').find(newRowSelector)
    .append(rebuildRopRow($(target).parents('tr').remove(), newStatus));

const deleteRule = ({target}) => {
  const tr = $(target).parents('tr');
  updateRuleStatus(tr.data('rop-id'), RULE_DELETED);
  tr.remove();
};
const archiveRule = ({target}) => {
  updateRuleStatus($(target).data('rop-id'), RULE_ARCHIVED);
  rebuildRowIn(target, RULE_ARCHIVED, 'tbody.archived');
};
const restoreRule = ({target}) => {
  updateRuleStatus($(target).data('rop-id'), RULE_USED);
  rebuildRowIn(target, RULE_USED, 'tbody.active');};
const copyRule = e => loadRuleRow(e, true);

const rebuildRopRow = (tr, newStatus) => {
  const rebuiltRow = $(buildRopRow(tr.data('rop-name'), tr.data('rop-id'), newStatus));
  attachRopRowButtonHandlers(rebuiltRow);
  return rebuiltRow;
};

const attachRopRowButtonHandlers = ruleRows => {
  ruleRows.find('.rule-delete').click(deleteRule);
  ruleRows.find('.rule-edit').click(loadRuleRow);
  ruleRows.find('.rule-archive').click(archiveRule);
  ruleRows.find('.rule-restore').click(restoreRule);
  ruleRows.find('.rule-copy').click(copyRule);
};

const obstypesPromise = Promise.all([promiseData({
    '': '&nbsp;',
    TAIR: 'Air Temperature (F)',
    TDEW: 'Dew Point Temperature (F)',
    RH: 'Relative Humidity (%)',
    STG: 'Flood Stage (ft)',
    DPHLNK: 'Link Depth (in)',
    DPHLIQ: 'Liquid Inundation Depth (in)',
    TPVT: 'Pavement Temperature (F)',
    RTEPC: 'Precipitation Rate Surface (in/hr)',
    PRBAR: 'Barometric Pressure (psi)',
    PRSUR: 'Surface Pressure (psi)',
    DPHSN: 'Snow Inundation Depth (in)',
    SPDLNK: 'Average Speed of Vehicles (mph)',
    TSSRF: 'Subsurface Temperature (F)',
    TRFLNK: 'Traffic (%)',
    VIS: 'Surface Visibility (mi)',
    DIRWND: 'Wind Direction (º)',
    GSTWND: 'Wind Gust Speed (mph)',
    SPDWND: 'Wind Speed (mph)'
  }), delay(750)]).then(([response]) => response);

const obstypeMap = new Map();
obstypesPromise.then(obstypes => Object.entries(obstypes).forEach(([key, value]) => obstypeMap.set(key, value)));


const operatorMap = new Map([['', '&nbsp;'], ['lt', '&lt;'], ['eq', '='], ['gt', '&gt;']]);
const comparisonTypeMap = new Map([['any', 'Any'], ['mean', 'Mean'], ['mode', 'Mode']]);


const buildRuleRow = rule => `<tr><td>${comparisonTypeMap.get(rule.type)}</td>
                <td style="text-align: left;">${obstypeMap.get(rule.obstype)}</td>
                <td>${operatorMap.get(rule.operator)}</td>
                <td>${rule.val}</td>
                <td>${rule.tol}</td>
                <td> <button type="button" class="w3-button w3-red rop-delete">Delete<i class="fas fa-times"></i></td></tr>`;


const buildControlRow = (control, controlDefMap) => {
  const controlDef = controlDefMap.get(control.type);
  const buildInputHtml = input => {
    let inputValue = control.inputs[input.id];
    if (input.type === INPUT_LIST)
      inputValue = input.options[inputValue];

    return `
    <div>
      <span>${inputValue}</span>
      <span>${input.type !== INPUT_LIST ? input.label : ''}</span>
    </div>`;
  };

  const inputHtml = controlDef.inputs ? controlDef.inputs.reduce((inputHtml, input) => inputHtml + buildInputHtml(input), '') : '';

  return `
  <tr>
    <td>${controlDef.label}</td>
    <td style="text-align: left;">${inputHtml}</td>
    <td> <button type="button" class="w3-button w3-red rop-delete">Delete<i class="fas fa-times"></i></td>
  </tr>`;
};

const buildRopRow = (name, id, status) => `
          <tr data-rop-id="${id}" data-rop-name="${name}">
						<td>${name}</td>
            ${status === RULE_UNUSED ? '<td><button type="button" class="w3-button w3-blue rule-edit">Edit<i class="fas fa-edit"></i></button></td>' : ''}
            ${status !== RULE_UNUSED ? '<td></td>' : ''}
            <td><button type="button" class="w3-button w3-indigo rule-copy">Copy<i class="fas fa-copy"></i></button></td>
            ${status === RULE_UNUSED ? '<td><button type="button" class="w3-button w3-red rule-delete">Delete<i class="fas fa-times"></i></button></td>' : ''}
            ${status === RULE_USED ? '<td><button type="button" class="w3-button w3-pink rule-archive">Archive<i class="fas fa-box"></i></button></td>' : ''}
            ${status === RULE_ARCHIVED ? '<td><button type="button" class="w3-button w3-green rule-restore">Restore<i class="fas fa-box-open"></i></button></td>' : ''}
          </tr>`;

const displayExistingRules = rules => {
  const ropContainer = $('.existing-rop-container');
  ropContainer.find('.loading-spinner').remove();
  const ropTabs = $(`
    <div class="w3-bar w3-dark-gray">
        <span data-class="unused" class="w3-bar-item w3-button">Unused</span>
        <span data-class="active" class="w3-bar-item w3-button">Active</span>
        <span data-class="archived" class="w3-bar-item w3-button">Archived</span>
    </div>`)
    .appendTo(ropContainer);


  const ropTable = $(`
    <table class="w3-table w3-table-all"> 
      <tbody class="unused"></tbody>
      <tbody class="active"></tbody>
      <tbody class="archived"></tbody>
    </table>`).appendTo(ropContainer);

  ropTabs
    .find('span')
    .click(({target}) => {
      ropTabs.find('span').removeClass('w3-light-gray');
      $(target).addClass('w3-light-gray');
      ropTable.find('tbody').hide();
      ropTable.find('tbody.' + $(target).data('class')).show();
    });

  const archivedRules = rules.filter(rule => rule.status === RULE_ARCHIVED);
  const activeRules = rules.filter(rule => rule.status === RULE_USED);
  const unusedRules = rules.filter(rule => rule.status === RULE_UNUSED);

  const activeRuleRows = $(activeRules.reduce((ruleRows, rule) => ruleRows + buildRopRow(rule.name, rule.id, rule.status), ''))
    .appendTo(ropTable.find('tbody.active'));

  const unusedRuleRows = $(unusedRules.reduce((ruleRows, rule) => ruleRows + buildRopRow(rule.name, rule.id, rule.status), ''))
    .appendTo(ropTable.find('tbody.unused'));

  const archivedRuleRows = $(archivedRules.reduce((ruleRows, rule) => ruleRows + buildRopRow(rule.name, rule.id, rule.status), ''))
    .appendTo(ropTable.find('tbody.archived'));

  attachRopRowButtonHandlers(activeRuleRows.add(unusedRuleRows).add(archivedRuleRows));

  ropTabs.find('span').first().click();
};

async function saveRuleHandler()
{
  const ropName = $('#rop-name');
  const existingRopId = ropName.data('rop-id');

  if (!ropName.get(0).checkValidity())
  {
    alert('Please enter a valid name');
    return;
  }

  const rules = [];
  $('#table-rule tbody tr')
    .each((idx, tr) => rules.push($(tr).data('rop')));


  const controls = [];
  $('#table-control tbody tr')
    .each((idx, tr) => controls.push($(tr).data('control')));

  if (controls.length === 0)
  {
    alert('Please add one or more controls');
    return;
  }

  const name = $('#rop-name').val();

  const saveData = {rules, name, controls};
  if (existingRopId)
    saveData.id = existingRopId;

  const savedResponse = await saveRule(saveData);
//  console.log(JSON.stringify(existingRules.get(id)));
  if (existingRopId)
  {
    $('.existing-rop-container tbody.unused').find('tr[data-rop-id="' + existingRopId + '"]')
      .data('rop-name', name)
      .find('td').eq(0).text(name);
  }
  else
  {
    attachRopRowButtonHandlers($(buildRopRow(name, savedResponse.id, RULE_UNUSED))
      .appendTo('.existing-rop-container tbody.unused'));
  }

  resetForm();
}

const resetForm = () => {
  $('#table-rule tbody').empty();
  $('#table-control tbody').empty();
  $('#rop-name')
    .val('')
    .data('rop-id', null);
};

const addControlEval = () => {
  const selectedControlType = $('#rop-controls option:selected');
  const controlInputs = $('#rop-control-inputs').find('input, select');

  let foundInvalidInput = false;
  controlInputs.filter('input').each((i, el) => {
    if (!el.checkValidity())
    {
      foundInvalidInput = true;
      alert('Please enter valid input values');
      return false;
    }
  });

  if (foundInvalidInput)
    return;

  const controlData = {
    type: selectedControlType.val()
  };
  const inputData = controlData.inputs = {};

  controlInputs
    .filter('select')
    .each((i, el) => {
      inputData[$(el).prop('name')] = $(el).find("option:selected").val();
    });

  controlInputs
    .filter('input')
    .each((i, el) => {
      inputData[$(el).prop('name')] = $(el).val();
    });

  const newRow = $(buildControlRow(controlData, controlDefMap));

  newRow.data('control', controlData);

  newRow.appendTo('#table-control tbody').find('.rop-delete').click(deleteParentRow);

  selectedControlType.parents('select').prop('selectedIndex', 0).change();
};

const addRuleEval = () => {
  const selectedEvalType = $('#rop-type option:selected');
  const selectedOperator = $('#rule-operator option:selected');
  const selectedObstype = $('#rop-obstypes option:selected');
  const ropVal = $('#rop-val');
  const ropTol = $('#rop-tol');

  if (!ropVal.get(0).checkValidity())
  {
    alert('Please enter a valid value');
    return;
  }

  if (!ropTol.get(0).checkValidity())
  {
    alert('Please enter a valid tolerance');
    return;
  }

  if (!selectedOperator.parents('select').get(0).checkValidity())
  {
    alert('Please select an operator');
    return;
  }

  if (!selectedObstype.parents('select').get(0).checkValidity())
  {
    alert('Please select an obstype');
    return;
  }

  const ropData = {
    tol: ropTol.val(),
    val: ropVal.val(),
    obstype: selectedObstype.val(),
    operator: selectedOperator.val(),
    type: selectedEvalType.val()
  };

  const newRow = $(buildRuleRow(ropData));
  newRow.data('rop', ropData);

  newRow.appendTo('#table-rule tbody').find('.rop-delete').click(deleteParentRow);

  for (let selectedOption of [selectedEvalType, selectedObstype, selectedOperator])
    selectedOption.parents('select').prop('selectedIndex', 0);
  for (let input of [ropVal, ropTol])
    input.val('');
};

function init()
{
  obstypesPromise.then(obs => $('#rop-obstypes')
      .empty()
      .append(Object.entries(obs).reduce(entriesToOptions)));

  let controlOptions = '';
  controlDefMap.forEach((controlDef, id) => {
    controlOptions += `<option value="${id}">${controlDef.label}</option>`;
  });
  $('#rop-controls').empty().append(controlOptions)
    .change(e => {
      const inputContainer = $('#rop-control-inputs');
      inputContainer.empty();
      const controlDef = controlDefMap.get($(e.target).val());
      if (controlDef.inputs)
        inputContainer.append(controlDef.inputs.reduce((inputHtml, input) => inputHtml + buildControlInputBlock(input), ''));
    });

  existingRulesPromise.then(displayExistingRules);

  $('#add-rop-rule').click(addRuleEval);
  $('#add-control-rule').click(addControlEval);

  $('.rule-save').click(saveRuleHandler);
  $('.rule-form-clear').click(resetForm);
}

$(document).on("initPage", init);