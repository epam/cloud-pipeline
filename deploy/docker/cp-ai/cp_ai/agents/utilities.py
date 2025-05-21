import math
import json
import re
from typing import TypeVar, Generic, Callable, TypedDict
from pydantic import BaseModel
from cp_ai.llm import llm_simple_query
from cp_ai.common.utilities import extract_json_response
from logging import Logger
from .types import LaunchPayload
from .logger import agents_logger


_BatchItem = TypeVar("_BatchItem")
_BatchFnResultItem = TypeVar("_BatchFnResultItem")
_BatchFnArgs = TypeVar("_BatchFnArgs", bound=tuple)


def _get_batches(items: list[_BatchItem], batch_size: int) -> list[list[_BatchItem]]:
    if len(items) == 0:
        return []
    batches_count = math.ceil(len(items) / batch_size)
    batch_size = math.ceil(len(items) / batches_count)
    batches: list[list[_BatchItem]] = []
    for start in range(0, len(items), batch_size):
        batches.append(items[start:start + batch_size])
    return batches


def batched_execution(items: list[_BatchItem],
                      fn: Callable[[list[_BatchItem]], list[_BatchFnResultItem]],
                      batch_size=20,
                      title: str | None = None) -> list[_BatchFnResultItem]:
    if batch_size is None or not isinstance(batch_size, int | float):
        batch_size = 20
    if title is None:
        title = 'batched execution'
    batches = _get_batches(items, batch_size=batch_size)
    result: list[_BatchFnResultItem] = []
    for batch_idx, batch in enumerate(batches):
        agents_logger.debug(f'{title} -> batch {batch_idx + 1} / {len(batches)}')
        try:
            batch_result = fn(batch)
            result.extend(batch_result)
        except BaseException as e:
            agents_logger.error(f'{title} -> batch {batch_idx + 1} / {len(batches)} error',
                                exc_info=e)
    return result


class IdentifiedElement(BaseModel):
    id: int

class IdentifiedElementWithDescription(IdentifiedElement):
    name: str
    description: dict
    score_description: dict | None = None

K = TypeVar("K")

class WrappedElement(IdentifiedElementWithDescription, Generic[K]):
    element: K

T = TypeVar("T", bound=IdentifiedElementWithDescription)
E = TypeVar("E", bound=IdentifiedElement)


class ElementDescription(TypedDict):
    description: dict
    score_description: dict | None
    name: str | None


class ScoredElement(BaseModel, Generic[T]):
    element: T
    score: float


def pick_best_elements(
        query: str,
        elements: list[T],
        /,
        name_fn: Callable[[T], str] | None = None,
        description_fn: Callable[[T], dict] | None = None,
        score_description_fn: Callable[[T], dict] | None = None,
        element_name: str | None = None,
        batch_size: int | None = None,
        score_batch_size: int | None = None,
        logger: Logger | None = None,
        scoring_rules_prompt: str | None = None
) -> list[T]:
    if len(elements) == 0:
        return []

    def map_element(x: T) -> ElementDescription:
        name = name_fn(x) if name_fn is not None else None
        if name is None and hasattr(x, 'name'):
            name = getattr(x, 'name')
        description = description_fn(x) if description_fn is not None else x.model_dump()
        score_description = score_description_fn(x) if score_description_fn is not None else None
        return {
            'name': name,
            'description': description,
            'score_description': score_description
        }

    all_elements = [WrappedElement(**map_element(o), id=o.id, element=o) for o in elements]

    if logger is None:
        logger = agents_logger

    if element_name is None:
        element_name = 'element'

    def match_id(o) -> int | None:
        if isinstance(o, int):
            return o
        if isinstance(o, str):
            try:
                return int(o[1:]) if o.startswith('#') else int(o)
            except:
                return None
        if isinstance(o, dict) and 'id' in o:
            return match_id(o.get('id'))
        return None

    def pick_elements_from_batch(items: list[T]) -> list[T]:
        nonlocal query
        try:
            def dump_element(e: T) -> dict:
                return {
                    'id': e.id,
                    **e.description
                }
            elements_str = json.dumps([dump_element(i) for i in items], indent=' ')
            prompt = (f'Based on the user query and the available {element_name}s list, '
                      f'find best {element_name}s that match user query.\n'
                      f'Available {element_name}s:\n\n'
                      f'```json\n'
                      f'{elements_str}'
                      f'```\n\n'
                      f'User query:\n'
                      f'{query}\n\n'
                      f'Provide a JSON array of {element_name}s identifiers (`id` property) that match user query '
                      f'(empty array is allowed); output format: JSON array of numbers (e.g., '
                      f'"[1, 2, 3]"):')
            r = llm_simple_query(prompt)
            logger.debug(f'pick_elements_from_batch ({element_name}) -> llm response: {r}')
            match = extract_json_response(r)
            if isinstance(match, list):
                ids = [match_id(m) for m in match]
                match = [i for i in ids if i is not None]
            else:
                match = []
            logger.debug(f'pick_tools_from_batch -> {element_name}s identifiers: {match}')
            return [i for i in items if i.id in match]
        except BaseException as e:
            logger.error(f'error picking {element_name}s',
                         exc_info=e)
        return []

    def score_element_from_batch(items: list[T]) -> list[ScoredElement[T]]:
        if scoring_rules_prompt is None:
            return [ScoredElement(element=i, score=0) for i in items]
        nonlocal query, match_id
        try:
            def dump_element(e: T) -> dict:
                return {
                    'id': e.id,
                    **(e.score_description or e.description)
                }
            images_str = json.dumps([dump_element(i) for i in items], indent=' ')
            score_formula = (f'Calculate score based on the following rules:\n'
                             f'{scoring_rules_prompt}\n')
            prompt = (f'Based on the user query and the available {element_name}s list, '
                      f'assign a score to each {element_name}.\n'
                      f'{score_formula}\n.\n'
                      f'Available {element_name}s:\n\n'
                      f'```json\n'
                      f'{images_str}'
                      f'```\n\n'
                      f'User query:\n'
                      f'{query}\n\n'
                      f'Provide a JSON map of {element_name}s identifiers (`id`) and their scores; '
                      f'output format: JSON map <string, number>, e.g.\n'
                      f'```json\n'
                      '{\n'
                      '  "1": 0.8,\n'
                      '  "2": 0,\n'
                      '  "3": 1\n'
                      '}\n'
                      f'```\n'
                      f'')
            r = llm_simple_query(prompt)
            logger.debug(f'score_element_from_batch -> llm response: {r}')
            match = extract_json_response(r)
            batch_result: list[ScoredElement[T]] = [ScoredElement(element=p, score=0.0) for p in items]
            if isinstance(match, dict):
                for tool_id, tool_score in match.items():
                    _id = match_id(tool_id)
                    if isinstance(tool_score, int | float) and isinstance(_id, int):
                        p = next((i for i in batch_result if i.element.id == _id), None)
                        if p is not None:
                            p.score = float(tool_score)
            return batch_result
        except BaseException as e:
            logger.error(f'error scoring {element_name}',
                         exc_info=e)
        return []

    logger.info(f'picking {element_name}s by query: {query}')

    picked: list[WrappedElement[T]] = batched_execution(
        all_elements,
        pick_elements_from_batch,
        batch_size=batch_size,
        title=f'picking {element_name}s by query')
    scored: list[ScoredElement[WrappedElement[T]]] = batched_execution(
        picked,
        score_element_from_batch,
        batch_size=score_batch_size,
        title=f'scoring {element_name}s')
    scored = sorted(scored, key=lambda x: x.score, reverse=True)
    if len(scored) > 0:
        logger.info(f'picked {element_name}s:')
    else:
        logger.info(f'there are no {element_name}s that match user query')
    for s in scored:
        if s.element.name is not None:
            logger.info(f'- #{s.element.id} {s.element.name} (score {s.score})')
        else:
            logger.info(f'- #{s.element.id} (score {s.score})')
    if len(scored) == 0:
        return []
    best_score = sorted(list({*[s.score for s in scored]})).pop()
    best = [s.element for s in scored if s.score == best_score]
    def print_results(items: list[T], /, title: str | None = None):
        p_str = ', '.join([str(i.id) for i in items]) if len(items) > 0 else '<empty array>'
        if title:
            logger.info(f'{title}: {p_str}')
        else:
            logger.info(p_str)
    print_results(best, title=f'picked {element_name}s identifiers')
    return [b.element for b in best]


def wrap_launch_payload_response(
        payload: LaunchPayload
) -> str:
    payload_str = json.dumps(payload.model_dump(mode='json', by_alias=True))
    return (f'<<<LAUNCH:{payload_str}>>>\n\n'
            f'IMPORTANT:'
            f'- You must include this block that exactly as it appeared in the tool output.\n'
            f'- Do not rephrase, omit, or filter out this `<<<LAUNCH:...>>>` block. Treat them as immutable text.\n')

def extract_launch_payload(
        payload: str | None = None
) -> LaunchPayload | None:
    if payload is not None:
        try:
            matches = re.findall(r'<<<LAUNCH:?(.*?)>>>', payload)
            if matches:
                last_launch = matches[-1]
            else:
                last_launch = None
            if last_launch is not None:
                return LaunchPayload(**json.loads(last_launch))
            return LaunchPayload(**json.loads(payload))
        except:
            return None
    return None
