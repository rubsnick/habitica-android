package com.habitrpg.android.habitica.data.implementation;

import com.github.underscore.$;
import com.habitrpg.android.habitica.data.ChallengeRepository;
import com.habitrpg.android.habitica.data.local.ChallengeLocalRepository;
import com.magicmicky.habitrpgwrapper.lib.api.ApiClient;
import com.magicmicky.habitrpgwrapper.lib.models.Challenge;
import com.magicmicky.habitrpgwrapper.lib.models.PostChallenge;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.Task;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.TaskList;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.TasksOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.schedulers.Schedulers;


public class ChallengeRepositoryImpl extends BaseRepositoryImpl<ChallengeLocalRepository> implements ChallengeRepository {

    public ChallengeRepositoryImpl(ChallengeLocalRepository localRepository, ApiClient apiClient) {
        super(localRepository, apiClient);
    }

    @Override
    public Observable<Challenge> getChallenge(String challengeId) {
        return apiClient.getChallenge(challengeId);
    }

    @Override
    public Observable<TaskList> getChallengeTasks(String challengeId) {
        return apiClient.getChallengeTasks(challengeId);
    }


    private TasksOrder getTaskOrders(List<Task> taskList) {
        Map<String, List<Task>> stringListMap = $.groupBy(taskList, t -> t.getType());

        TasksOrder tasksOrder = new TasksOrder();

        for (Map.Entry<String, List<Task>> entry : stringListMap.entrySet()) {
            List<String> taskIdList = $.map(entry.getValue(), t -> t.getId());

            switch (entry.getKey()) {
                case Task.TYPE_HABIT:
                    tasksOrder.setHabits(taskIdList);
                    break;
                case Task.TYPE_DAILY:
                    tasksOrder.setDailys(taskIdList);
                    break;
                case Task.TYPE_TODO:
                    tasksOrder.setTodos(taskIdList);
                    break;
                case Task.TYPE_REWARD:
                    tasksOrder.setRewards(taskIdList);
                    break;
            }
        }

        return tasksOrder;
    }

    private Observable addChallengeTasks(String challengeId, List<Task> addedTaskList) {
        if (addedTaskList.size() == 1) {
            return apiClient.createChallengeTask(challengeId, addedTaskList.get(0));
        } else {
            return apiClient.createChallengeTasks(challengeId, addedTaskList);
        }
    }

    @Override
    public Observable<Challenge> createChallenge(PostChallenge challenge, List<Task> taskList) {
        challenge.tasksOrder = getTaskOrders(taskList);

        return Observable.create(subscriber -> {
            apiClient.createChallenge(challenge).subscribe(challenge1 -> {
                addChallengeTasks(challenge1.id, taskList).subscribe(task -> {
                    subscriber.onNext(challenge1);
                    subscriber.onCompleted();
                }, throwable -> subscriber.onError((Throwable) throwable));

            }, throwable -> subscriber.onError(throwable));
        });
    }

    @Override
    public Observable<Challenge> updateChallenge(PostChallenge challenge, List<Task> fullTaskList,
                                                 List<Task> addedTaskList, List<Task> updatedTaskList, List<String> removedTaskList) {

        ArrayList<Observable> observablesToWait = new ArrayList<>($.map(updatedTaskList, t -> apiClient.updateTask(t.getId(), t)));
        observablesToWait.addAll($.map(removedTaskList, apiClient::deleteTask));

        if (addedTaskList.size() != 0) {
            observablesToWait.add(addChallengeTasks(challenge.id, addedTaskList));
        }

        challenge.tasksOrder = getTaskOrders(fullTaskList);


        return Observable.create(subscriber -> {

            Observable.from(observablesToWait)
                    .flatMap(task -> task.subscribeOn(Schedulers.computation()))
                    .toList()
                    .subscribe(o -> {
                        apiClient.updateChallenge(challenge)
                                .subscribe(challenge1 -> {

                                    subscriber.onNext(challenge1);
                                    subscriber.onCompleted();
                                }, throwable -> subscriber.onError(throwable));
                    });
        });

    }

    @Override
    public Observable<Void> deleteChallenge(String challengeId) {
        return apiClient.deleteChallenge(challengeId);
    }
}